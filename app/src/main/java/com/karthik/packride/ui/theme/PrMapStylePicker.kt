package com.karthik.packride.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.maps.android.compose.MapType

/** Android twin of iOS MapStylePickerView: Satellite, Standard, Hybrid. */
@Composable
fun PrMapStylePicker(
    selected: MapType,
    onSelected: (MapType) -> Unit,
    modifier: Modifier = Modifier
) {
    val choices = listOf(
        Triple(MapType.SATELLITE, Icons.Filled.Public, "Satellite"),
        Triple(MapType.NORMAL, Icons.Filled.Map, "Standard"),
        Triple(MapType.HYBRID, Icons.Filled.DirectionsCar, "Hybrid")
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Pr.cardBg)
            .border(1.dp, Pr.border, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        choices.forEach { (type, icon, label) ->
            MapStyleChoice(icon, label, selected == type) { onSelected(type) }
        }
    }
}

@Composable
private fun MapStyleChoice(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Pr.coralSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Pr.coral else Pr.muted, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
            color = if (selected) Pr.coral else Pr.muted
        )
    }
}
