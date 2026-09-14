package com.karthik.packride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.safety.EmergencyContact
import com.karthik.packride.safety.EmergencyContactStore
import com.karthik.packride.ui.theme.PrCoral

@Composable
fun EmergencyContactsScreen() {
    val context = LocalContext.current
    val store = remember { EmergencyContactStore(context) }
    var contacts by remember { mutableStateOf(store.load()) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var linkedId by remember { mutableStateOf<String?>(null) }

    val friendsMgr = remember { FriendsManager(context) }
    val following by friendsMgr.followedUsers.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { friendsMgr.start() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { friendsMgr.stop() }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Emergency contacts", style = MaterialTheme.typography.headlineMedium, color = PrCoral)
        Text(
            "Up to ${EmergencyContactStore.MAX}. Linked PackRide friends get in-app crash alerts; phone is used for SMS.",
            style = MaterialTheme.typography.bodySmall
        )

        if (contacts.size < EmergencyContactStore.MAX) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(relationship, { relationship = it }, label = { Text("Relationship") }, modifier = Modifier.fillMaxWidth())
            Text("Link PackRide friend (optional)", style = MaterialTheme.typography.labelMedium)
            following.take(8).forEach { f ->
                OutlinedButton(
                    onClick = { linkedId = if (linkedId == f.id) null else f.id },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (linkedId == f.id) "✓ ${f.name}" else f.name)
                }
            }
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    contacts = contacts + EmergencyContact(
                        name = name.trim(),
                        phone = phone.trim(),
                        relationship = relationship.trim(),
                        linkedUserID = linkedId
                    )
                    store.save(contacts)
                    name = ""; phone = ""; relationship = ""; linkedId = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add contact") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(c.relationship, c.phone).filter { it.isNotBlank() }.joinToString(" · "))
                        if (c.linkedUserID != null) {
                            Text("Linked PackRide user", style = MaterialTheme.typography.bodySmall, color = PrCoral)
                        }
                        Row {
                            OutlinedButton(onClick = {
                                contacts = contacts.filterNot { it.id == c.id }
                                store.save(contacts)
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
