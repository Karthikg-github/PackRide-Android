/*
 * Merge this export into PackRide/packride-functions/functions/index.js and deploy it.
 * It uses the existing admin, db and functions instances declared in that file.
 * The callable must remain authenticated; never accept a UID from the client.
 */
exports.deletePackRideAccount = functions.https.onCall(async (_data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in is required.");
  }
  const uid = context.auth.uid;
  const [userSnap, postsSnap, ridersSnap, communitiesSnap, ridesSnap] = await Promise.all([
    db.ref(`users/${uid}`).once("value"),
    db.ref("feedPosts").once("value"),
    db.ref("publicRiders").once("value"),
    db.ref("communities").once("value"),
    db.ref("scheduledRides").once("value"),
  ]);
  const user = userSnap.val() || {};
  const updates = {
    [`users/${uid}`]: null,
    [`publicRiders/${uid}`]: null,
    [`helpRequests/${uid}`]: null,
    [`locationDeliveryIndex/${uid}`]: null,
  };
  Object.keys(user.following || {}).forEach((id) => { updates[`users/${id}/followers/${uid}`] = null; });
  Object.keys(user.followers || {}).forEach((id) => { updates[`users/${id}/following/${uid}`] = null; });
  ridersSnap.forEach((r) => { updates[`users/${r.key}/followRequests/${uid}`] = null; });
  postsSnap.forEach((post) => {
    const value = post.val() || {};
    if (value.authorID === uid) updates[`feedPosts/${post.key}`] = null;
    else {
      updates[`feedPosts/${post.key}/reactions/${uid}`] = null;
      updates[`feedPosts/${post.key}/likes/${uid}`] = null;
      updates[`feedPosts/${post.key}/bookmarks/${uid}`] = null;
      post.child("comments").forEach((comment) => {
        if (comment.child("userID").val() === uid) updates[`feedPosts/${post.key}/comments/${comment.key}`] = null;
      });
    }
  });
  communitiesSnap.forEach((community) => community.child("members").forEach((member) => {
    if (member.child("authUID").val() === uid) updates[`communities/${community.key}/members/${member.key}`] = null;
  }));
  ridesSnap.forEach((ride) => {
    if (ride.child("creatorID").val() === uid) updates[`scheduledRides/${ride.key}`] = null;
    else updates[`scheduledRides/${ride.key}/rsvps/${uid}`] = null;
  });
  await db.ref().update(updates);

  const bucket = admin.storage().bucket();
  await Promise.all([
    bucket.deleteFiles({ prefix: `users/${uid}/` }),
    ...Object.keys(updates)
      .filter((path) => /^feedPosts\/[^/]+$/.test(path))
      .map((path) => bucket.file(`feedPhotos/${path.split("/")[1]}.jpg`).delete().catch(() => null)),
  ]);
  await admin.auth().deleteUser(uid);
  return { deleted: true };
});
