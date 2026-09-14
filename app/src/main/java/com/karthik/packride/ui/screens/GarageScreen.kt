package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthik.packride.garage.Bike
import com.karthik.packride.garage.GarageManager
import com.karthik.packride.garage.MaintenanceItem
import com.karthik.packride.garage.MaintenanceItemType
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrMetricStrip
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrimaryButton
import com.karthik.packride.ui.theme.PrWebSectionLabel

// Aug 30, 2026 — full visual + functionality parity pass, port of iOS
// GarageView.swift (726 lines; Android was an 85-line stub with no
// nickname/make/model split, no active-bike concept, no maintenance
// interval editing, and fake "+100 mi" buttons instead of real ride
// mileage). See GarageManager.kt for the model/persistence side of this.

private fun MaintenanceItemType.icon(): ImageVector = when (this) {
    MaintenanceItemType.OIL -> Icons.Default.Opacity
    MaintenanceItemType.CHAIN -> Icons.Default.Link
    MaintenanceItemType.TIRES -> Icons.Default.TripOrigin
    MaintenanceItemType.BRAKES -> Icons.Default.PanTool
    MaintenanceItemType.VALVES -> Icons.Default.Speed
}

private val statusGreen = Color(0xFF2E9E5B)
private val statusRed = Color(0xFFD33B2C)
private val statusAmber = Color(0xFFE8952F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen() {
    val context = LocalContext.current
    val manager = remember { GarageManager.get() }
    val historyManager = remember { RideHistoryManager(context) }
    val bikes by manager.bikes.collectAsState()
    val rides by historyManager.rides.collectAsState()
    val measurementSystem by com.karthik.packride.data.MeasurementUnits.system.collectAsState()

    var showAddBike by remember { mutableStateOf(false) }
    var editingBike by remember { mutableStateOf<Bike?>(null) }
    var intervalTarget by remember { mutableStateOf<Pair<String, MaintenanceItem>?>(null) }
    var deleteTarget by remember { mutableStateOf<Bike?>(null) }

    Box(Modifier.fillMaxSize().background(Pr.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PrPageHeader(
                eyebrow = "Garage",
                title = "Your Machines",
                subtitle = if (bikes.isEmpty()) "Keep every bike ready for the next road."
                else "${bikes.size} bike${if (bikes.size == 1) "" else "s"} in your garage"
            )

            if (bikes.isNotEmpty()) {
                val activeBike = bikes.firstOrNull { it.isActive }
                val activeMileage = activeBike?.let { manager.totalMileage(it, rides) } ?: 0.0
                val totalRideMiles = rides.sumOf { it.distanceMiles }
                PrMetricStrip(
                    metrics = listOf(
                        "${bikes.size}" to "Bikes",
                        com.karthik.packride.data.MeasurementUnits.distanceMiles(totalRideMiles, 0) to "Ride Distance",
                        com.karthik.packride.data.MeasurementUnits.distanceMiles(activeMileage, 0) to "Active Odo"
                    )
                )
            }

            PrWebSectionLabel(
                title = if (bikes.isEmpty()) "Start Here" else "Garage",
                detail = if (bikes.isEmpty()) null else "Maintenance + mileage"
            )

            if (bikes.isEmpty()) {
                EmptyGarageState(modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    bikes.forEach { bike ->
                        BikeCard(
                            bike = bike,
                            totalMileage = manager.totalMileage(bike, rides),
                            onSetActive = { manager.setActive(bike.id) },
                            onEdit = { editingBike = bike },
                            onDelete = { deleteTarget = bike },
                            onServiced = { itemType ->
                                manager.markServiced(bike.id, itemType, manager.totalMileage(bike, rides))
                            },
                            onEditInterval = { item -> intervalTarget = bike.id to item }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 32.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Pr.coral)
                    .clickable { showAddBike = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("ADD BIKE", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Color.White))
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }

    if (showAddBike) {
        AddEditBikeSheet(
            existing = null,
            onSave = { bike -> manager.addBike(bike); showAddBike = false },
            onDismiss = { showAddBike = false }
        )
    }
    editingBike?.let { bike ->
        AddEditBikeSheet(
            existing = bike,
            onSave = { updated -> manager.updateBike(updated); editingBike = null },
            onDismiss = { editingBike = null }
        )
    }
    intervalTarget?.let { (bikeId, item) ->
        EditIntervalSheet(
            item = item,
            onSave = { newInterval ->
                manager.updateInterval(bikeId, item.type, newInterval)
                intervalTarget = null
            },
            onDismiss = { intervalTarget = null }
        )
    }
    deleteTarget?.let { bike ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${bike.nickname}?") },
            text = { Text("This removes the bike from your Garage. Rides already logged to it stay in your history.") },
            confirmButton = {
                TextButton(onClick = { manager.deleteBike(bike.id); deleteTarget = null }) {
                    Text("Delete", color = statusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyGarageState(modifier: Modifier = Modifier) {
    PrCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(80.dp).background(Pr.coralSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Motorcycle, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("No bikes yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
            Spacer(Modifier.height(6.dp))
            Text(
                "Add your first bike to start tracking mileage and maintenance.",
                style = TextStyle(fontSize = 13.sp, color = Pr.muted),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun BikeCard(
    bike: Bike,
    totalMileage: Double,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onServiced: (MaintenanceItemType) -> Unit,
    onEditInterval: (MaintenanceItem) -> Unit
) {
    PrCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(50.dp)
                        .background(if (bike.isActive) Pr.coralSoft else Pr.fieldBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Motorcycle, contentDescription = null, tint = if (bike.isActive) Pr.coral else Pr.muted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bike.nickname, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
                        if (bike.isActive) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.background(Pr.coral, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                Text("ACTIVE", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = Color.White))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(bike.subtitle, style = TextStyle(fontSize = 13.sp, color = Pr.muted))
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.background(Pr.coralSoft, RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(8.dp)
                ) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Pr.coral, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.background(statusRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(8.dp)
                ) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = statusRed, modifier = Modifier.size(18.dp)) }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))

            Column {
                Text("TOTAL MILEAGE", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Pr.muted))
                Text(com.karthik.packride.data.MeasurementUnits.distanceMiles(totalMileage, 0), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MAINTENANCE", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Pr.muted))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bike.maintenanceItems.forEach { item ->
                        MaintenanceRow(
                            item = item,
                            currentMileage = totalMileage,
                            onServiced = { onServiced(item.type) },
                            onEditInterval = { onEditInterval(item) }
                        )
                    }
                }
            }

            if (!bike.isActive) {
                PrimaryButton(text = "Set as Active Bike", onClick = onSetActive)
            }
        }
    }
}

@Composable
private fun MaintenanceRow(
    item: MaintenanceItem,
    currentMileage: Double,
    onServiced: () -> Unit,
    onEditInterval: () -> Unit
) {
    val used = (currentMileage - item.lastServiceMileage).coerceAtLeast(0.0)
    val remaining = item.intervalMiles - used
    val isOverdue = remaining < 0
    val statusColor = when {
        isOverdue -> statusRed
        remaining <= item.intervalMiles * 0.2 -> statusAmber
        else -> statusGreen
    }
    val statusText = if (isOverdue) "Overdue by ${com.karthik.packride.data.MeasurementUnits.distanceMiles(-remaining, 0)}" else "${com.karthik.packride.data.MeasurementUnits.distanceMiles(remaining, 0)} left"

    Row(
        modifier = Modifier.fillMaxWidth().background(Pr.fieldBg, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(statusColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.type.icon(), contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.type.label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink))
                Spacer(Modifier.width(6.dp))
                Text(
                    "every ${com.karthik.packride.data.MeasurementUnits.distanceMiles(item.intervalMiles, 0)}",
                    style = TextStyle(fontSize = 11.sp, color = Pr.muted),
                    modifier = Modifier.clickable(onClick = onEditInterval)
                )
            }
            Text(statusText, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor))
        }
        Box(
            modifier = Modifier.background(Pr.teal.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable(onClick = onServiced).padding(horizontal = 10.dp, vertical = 7.dp)
        ) { Text("Serviced", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Pr.teal)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditIntervalSheet(item: MaintenanceItem, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    val metric = com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC
    var text by remember { mutableStateOf((if (metric) item.intervalMiles * 1.609344 else item.intervalMiles).toInt().toString()) }
    val value = text.toDoubleOrNull()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Edit ${item.type.label} Interval", style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
            Spacer(Modifier.height(4.dp))
            Text("How many ${if (metric) "kilometres" else "miles"} between services.", style = TextStyle(fontSize = 13.sp, color = Pr.muted))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text(if (metric) "Kilometres" else "Miles") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "Save", enabled = (value ?: 0.0) > 0, onClick = { value?.let { onSave(if (metric) it / 1.609344 else it) } })
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss) { Text("Cancel", color = Pr.muted) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditBikeSheet(existing: Bike?, onSave: (Bike) -> Unit, onDismiss: () -> Unit) {
    val units = com.karthik.packride.data.MeasurementUnits
    val metric by units.system.collectAsState()
    var nickname by remember { mutableStateOf(existing?.nickname ?: "") }
    var make by remember { mutableStateOf(existing?.make ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var year by remember { mutableStateOf(existing?.year ?: "") }
    var odometerText by remember(existing?.id, metric) {
        mutableStateOf(existing?.let { units.milesToDisplay(it.baselineOdometer).toInt().toString() } ?: "")
    }
    val isValid = nickname.isNotBlank() && odometerText.toDoubleOrNull() != null
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
            Text(
                if (existing == null) "Add Bike" else "Edit Bike",
                style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Pr.ink),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname (e.g. \"The Beast\")") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(make, { make = it }, label = { Text("Make (e.g. Yamaha)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(model, { model = it }, label = { Text("Model (e.g. MT-07)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(year, { year = it.filter { c -> c.isDigit() } }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                odometerText,
                { odometerText = it.filter { c -> c.isDigit() } },
                label = { Text("Starting odometer (${if (metric == com.karthik.packride.data.MeasurementSystem.METRIC) "km" else "mi"})") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The mileage already on this bike before you started tracking it in PackRide.",
                style = TextStyle(fontSize = 11.sp, color = Pr.muted)
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (existing == null) "Add Bike" else "Save Changes",
                enabled = isValid,
                onClick = {
                    val newOdometer = units.displayDistanceToMiles(odometerText.toDoubleOrNull() ?: 0.0)
                    if (existing != null) {
                        val delta = newOdometer - existing.baselineOdometer
                        val updatedItems = if (delta != 0.0) {
                            existing.maintenanceItems.map { it.copy(lastServiceMileage = it.lastServiceMileage + delta) }
                        } else existing.maintenanceItems
                        onSave(
                            existing.copy(
                                nickname = nickname.trim(),
                                make = make.trim(),
                                model = model.trim(),
                                year = year.trim(),
                                baselineOdometer = newOdometer,
                                maintenanceItems = updatedItems
                            )
                        )
                    } else {
                        onSave(
                            Bike(
                                nickname = nickname.trim(),
                                make = make.trim(),
                                model = model.trim(),
                                year = year.trim(),
                                baselineOdometer = newOdometer
                            )
                        )
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss) { Text("Cancel", color = Pr.muted) }
            Spacer(Modifier.height(20.dp))
        }
    }
}
