package biali.fitmanager

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biali.fitmanager.network.UserResponse
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80
import java.util.Calendar

sealed class SessionEditorMode {
    data class Create(
        val initialClientId: Int? = null,
        val initialClientName: String? = null
    ) : SessionEditorMode()

    data class EditDraft(
        val session: ClientTrainingSession,
        val exercises: List<ClientSessionExercise>
    ) : SessionEditorMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorSheet(
    mode: SessionEditorMode,
    clients: List<UserResponse>,
    exercises: List<ClientExercise>,
    onDismiss: () -> Unit,
    onCreate: (Int, String, String, Int, List<DraftExercise>, Boolean) -> Unit,
    onUpdateDraft: (Int, String, String, Int, Set<Int>, List<DraftExercise>) -> Unit,
    onSendToClient: (Int) -> Unit,
    onUpdateAndSend: (Int, String, String, Int, Set<Int>, List<DraftExercise>) -> Unit = { _, _, _, _, _, _ -> }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = mode is SessionEditorMode.EditDraft

    var title by remember {
        mutableStateOf(if (isEdit) (mode as SessionEditorMode.EditDraft).session.title else "")
    }
    var startTime by remember {
        mutableStateOf(
            if (isEdit) {
                val raw = (mode as SessionEditorMode.EditDraft).session.date
                when {
                    raw.contains("-") -> raw.substringBefore(" ")
                    else -> {
                        val m = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})").find(raw)
                        if (m != null) "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}" else ""
                    }
                }
            } else ""
        )
    }

    var selectedClientId by remember {
        mutableStateOf(
            when (mode) {
                is SessionEditorMode.Create -> mode.initialClientId
                is SessionEditorMode.EditDraft -> null
            }
        )
    }
    var clientSearch by remember {
        mutableStateOf(
            when (mode) {
                is SessionEditorMode.Create -> mode.initialClientName ?: ""
                is SessionEditorMode.EditDraft -> mode.session.clientName ?: ""
            }
        )
    }

    val draftList = remember {
        mutableStateListOf<DraftExercise>().apply {
            if (mode is SessionEditorMode.EditDraft) {
                addAll(
                    mode.exercises.map {
                        DraftExercise(it.exerciseId, it.exerciseName, it.sets, it.reps, it.weight, it.id)
                    }
                )
            }
        }
    }
    val originalExerciseIds = remember(mode) {
        if (mode is SessionEditorMode.EditDraft) mode.exercises.map { it.id }.toSet() else emptySet()
    }

    var exerciseSearch by remember { mutableStateOf("") }
    var selectedExercise by remember { mutableStateOf<ClientExercise?>(null) }
    var setsInput by remember { mutableStateOf("") }
    var repsInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var editingDraftIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                startTime = String.format("%04d-%02d-%02d", year, month + 1, day)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val filteredClients = clients.filter {
        "${it.firstName} ${it.lastName}".contains(clientSearch, ignoreCase = true)
    }
    val filteredExercises = exercises.filter {
        it.name.contains(exerciseSearch, ignoreCase = true) ||
            it.bodyPart.contains(exerciseSearch, ignoreCase = true)
    }.groupBy { it.bodyPart }.toSortedMap()

    fun resetExerciseForm() {
        selectedExercise = null
        exerciseSearch = ""
        setsInput = ""
        repsInput = ""
        weightInput = ""
        editingDraftIndex = null
    }

    fun buildDraftFromInputs(): DraftExercise? {
        val ex = selectedExercise ?: return null
        val sets = setsInput.toIntOrNull() ?: return null
        val reps = repsInput.toIntOrNull() ?: return null
        val isTime = ExercisePlanHelper.isTimeBased(ex.name)
        val weight = if (isTime) 0.0 else weightInput.replace(",", ".").toDoubleOrNull() ?: 0.0
        return DraftExercise(ex.id, ex.name, sets, reps, weight)
    }

    fun applyDraft() {
        val draft = buildDraftFromInputs() ?: return
        val editIdx = editingDraftIndex
        if (editIdx != null) {
            val existingId = draftList[editIdx].sessionExerciseId
            draftList[editIdx] = draft.copy(sessionExerciseId = existingId)
        } else {
            draftList.add(draft)
        }
        resetExerciseForm()
    }

    fun canSave(): Boolean = title.isNotBlank() && startTime.isNotBlank() &&
        (isEdit || selectedClientId != null)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFAFAFA),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (isEdit) "Edytuj szkic treningu" else "Nowy plan treningowy",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Green80
            )
            Text(
                text = if (isEdit) "Zmień ćwiczenia i szczegóły, potem zapisz lub wyślij do klienta"
                else "Wybierz klienta, ułóż plan i zapisz jako szkic lub wyślij od razu",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EditorSectionCard(title = "Szczegóły treningu") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nazwa treningu") },
                        placeholder = { Text("np. Trening Push, Nogi + core") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (startTime.isNotBlank()) ExercisePlanHelper.formatDisplayDate(startTime) else "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data treningu") },
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Filled.DateRange, contentDescription = "Wybierz datę")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                EditorSectionCard(title = if (isEdit) "Klient" else "Wybierz klienta") {
                    if (isEdit) {
                        Surface(
                            color = LightGreen80,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = clientSearch.ifBlank { "Nie przypisano" },
                                modifier = Modifier.padding(14.dp),
                                fontWeight = FontWeight.SemiBold,
                                color = Green80
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = clientSearch,
                            onValueChange = { clientSearch = it; selectedClientId = null },
                            label = { Text("Szukaj klienta") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (filteredClients.isEmpty()) {
                            Text("Brak pasujących klientów.", color = Color.Gray, fontSize = 13.sp)
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                            ) {
                                items(filteredClients) { client ->
                                    val fullName = "${client.firstName} ${client.lastName}".trim()
                                    val selected = selectedClientId == client.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedClientId = client.id
                                                clientSearch = fullName
                                            }
                                            .background(if (selected) LightGreen80 else Color.White)
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = fullName,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (selected) {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = Green80)
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFEEEEEE))
                                }
                            }
                        }
                    }
                }

                EditorSectionCard(title = "Dodaj ćwiczenie") {
                    OutlinedTextField(
                        value = exerciseSearch,
                        onValueChange = { exerciseSearch = it; selectedExercise = null },
                        label = { Text("Szukaj ćwiczenia") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (filteredExercises.isEmpty()) {
                        Text("Brak ćwiczeń pasujących do wyszukiwania.", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                        ) {
                            filteredExercises.forEach { (bodyPart, list) ->
                                item {
                                    Text(
                                        text = bodyPart,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF0F4F1))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Green80
                                    )
                                }
                                items(list) { exercise ->
                                    val selected = selectedExercise?.id == exercise.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedExercise = exercise; exerciseSearch = exercise.name }
                                            .background(if (selected) LightGreen80 else Color.White)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = exercise.name,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 14.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        if (selected) {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = Green80, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFEEEEEE))
                                }
                            }
                        }
                    }

                    if (selectedExercise != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isTime = ExercisePlanHelper.isTimeBased(selectedExercise!!.name)
                        Text(
                            text = selectedExercise!!.name,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF37474F),
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = setsInput,
                                onValueChange = { setsInput = it },
                                label = { Text("Serie") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = repsInput,
                                onValueChange = { repsInput = it },
                                label = { Text(if (isTime) "Czas (sek.)" else "Powtórzenia") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            if (!isTime) {
                                OutlinedTextField(
                                    value = weightInput,
                                    onValueChange = { weightInput = it },
                                    label = { Text("Kg") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { applyDraft() },
                            enabled = buildDraftFromInputs() != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (editingDraftIndex != null) Icons.Filled.Edit else Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (editingDraftIndex != null) "Zaktualizuj na liście" else "Dodaj do planu")
                        }
                    }
                }

                if (draftList.isNotEmpty()) {
                    EditorSectionCard(title = "Plan (${draftList.size} ćwiczeń)") {
                        draftList.forEachIndexed { index, draft ->
                            val isEditing = editingDraftIndex == index
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isEditing) Color(0xFFE3F2FD) else Color.White
                                ),
                                elevation = CardDefaults.cardElevation(1.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(draft.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            ExercisePlanHelper.formatPlan(draft.name, draft.sets, draft.reps, draft.weight),
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(onClick = {
                                        editingDraftIndex = index
                                        selectedExercise = exercises.find { it.id == draft.exerciseId }
                                            ?: ClientExercise(draft.exerciseId, draft.name, "")
                                        exerciseSearch = draft.name
                                        setsInput = draft.sets.toString()
                                        repsInput = draft.reps.toString()
                                        weightInput = if (draft.weight > 0) draft.weight.toString() else ""
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edytuj", tint = Color(0xFF1E88E5))
                                    }
                                    IconButton(onClick = {
                                        draftList.removeAt(index)
                                        if (editingDraftIndex == index) resetExerciseForm()
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Usuń", tint = Color(0xFFE53935))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Anuluj") }

                if (isEdit) {
                    val sessionId = (mode as SessionEditorMode.EditDraft).session.id
                    Button(
                        onClick = {
                            if (!canSave()) return@Button
                            val time = if (startTime.contains("T")) startTime else "${startTime}T12:00:00"
                            onUpdateDraft(sessionId, title, time, 60, originalExerciseIds, draftList.toList())
                            onDismiss()
                        },
                        enabled = canSave(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Zapisz zmiany") }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (!canSave()) return@OutlinedButton
                            val time = if (startTime.contains("T")) startTime else "${startTime}T12:00:00"
                            onCreate(selectedClientId!!, title, time, 60, draftList.toList(), false)
                            onDismiss()
                        },
                        enabled = canSave(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Szkic") }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (!canSave() || draftList.isEmpty()) return@Button
                    val time = if (startTime.contains("T")) startTime else "${startTime}T12:00:00"
                    if (isEdit) {
                        val sessionId = (mode as SessionEditorMode.EditDraft).session.id
                        onUpdateAndSend(sessionId, title, time, 60, originalExerciseIds, draftList.toList())
                    } else {
                        onCreate(selectedClientId!!, title, time, 60, draftList.toList(), true)
                    }
                    onDismiss()
                },
                enabled = canSave() && draftList.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green80),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Wyślij plan do klienta", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EditorSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Green80)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddExercisesSheet(
    exercises: List<ClientExercise>,
    onDismiss: () -> Unit,
    onSubmit: (List<DraftExercise>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var exerciseSearch by remember { mutableStateOf("") }
    var selectedExercise by remember { mutableStateOf<ClientExercise?>(null) }
    var setsInput by remember { mutableStateOf("") }
    var repsInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    val draftList = remember { mutableStateListOf<DraftExercise>() }

    val filtered = exercises.filter {
        it.name.contains(exerciseSearch, ignoreCase = true) || it.bodyPart.contains(exerciseSearch, ignoreCase = true)
    }.groupBy { it.bodyPart }.toSortedMap()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFAFAFA)
    ) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("Dodaj ćwiczenia do planu", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green80)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = exerciseSearch,
                onValueChange = { exerciseSearch = it; selectedExercise = null },
                label = { Text("Szukaj") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            ) {
                filtered.forEach { (part, list) ->
                    item {
                        Text(
                            part,
                            Modifier.fillMaxWidth().background(Color(0xFFF0F4F1)).padding(8.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Green80
                        )
                    }
                    items(list) { ex ->
                        val sel = selectedExercise?.id == ex.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedExercise = ex; exerciseSearch = ex.name }
                                .background(if (sel) LightGreen80 else Color.White)
                                .padding(12.dp)
                        ) {
                            Text(ex.name, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                    }
                }
            }

            selectedExercise?.let { ex ->
                Spacer(Modifier.height(12.dp))
                val isTime = ExercisePlanHelper.isTimeBased(ex.name)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(setsInput, { setsInput = it }, label = { Text("Serie") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(repsInput, { repsInput = it }, label = { Text(if (isTime) "Sek." else "Powt.") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (!isTime) {
                        OutlinedTextField(weightInput, { weightInput = it }, label = { Text("Kg") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
                Button(
                    onClick = {
                        val s = setsInput.toIntOrNull() ?: return@Button
                        val r = repsInput.toIntOrNull() ?: return@Button
                        val w = if (isTime) 0.0 else weightInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                        draftList.add(DraftExercise(ex.id, ex.name, s, r, w))
                        selectedExercise = null
                        exerciseSearch = ""
                        setsInput = ""
                        repsInput = ""
                        weightInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) { Text("Dodaj") }
            }

            if (draftList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                draftList.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(d.name, fontWeight = FontWeight.SemiBold)
                            Text(ExercisePlanHelper.formatPlan(d.name, d.sets, d.reps, d.weight), fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { draftList.remove(d) }) {
                            Icon(Icons.Filled.Close, null, tint = Color.Red)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (draftList.isNotEmpty()) onSubmit(draftList.toList()) },
                enabled = draftList.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green80)
            ) { Text("Zapisz do planu", color = Color.White) }
        }
    }
}
