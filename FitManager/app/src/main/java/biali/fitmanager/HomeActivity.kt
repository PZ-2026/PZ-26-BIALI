package biali.fitmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80
import biali.fitmanager.ui.theme.GymManagerTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { Navbar() },
                    bottomBar = {BottomNav()}
                ) { innerPadding ->
                    MainContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Navbar() {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FitDay", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("230.00zł", fontSize = 16.sp)
            }
        }
    )
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Mój panel", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = "Witaj Jan Nowak!", fontSize = 16.sp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(color = Color.LightGray)
                .padding(16.dp)
        ) {
            Text(text = "Aktywny karnet", modifier = Modifier.align(Alignment.Center))
        }

        Text(text = "Twoje ostatnie treningi", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        // Trening 1
        TrainingTable(date = "17.03.2026 r.")

        // Trening 2
        TrainingTable(date = "15.03.2026 r.")

        // Przycisk na samym dole
        Button(
            onClick = { println("Nawigacja do nowej strony...") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White)
        ) {
            Text("Rozpocznij nowy trening", color = Color.White)
        }

        // Mały odstęp na samym dole, żeby przycisk nie dotykał krawędzi ekranu
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Wyodrębniłem tabelę do osobnej funkcji, żeby nie powtarzać kodu (DRY)
@Composable
fun TrainingTable(date: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = date,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            TableCell(text = "Nazwa ćwiczenia", weight = 2f, isHeader = true)
            TableCell(text = "Ciężar", weight = 1f, isHeader = true)
            TableCell(text = "Powtórzenia", weight = 1f, isHeader = true)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color.LightGray)

        TrainRow("Podciągania", "10kg", "7 7 7 7 7")
        TrainRow("Martwy ciąg", "100kg", "7 8 6 9 5")
        TrainRow("Wiosłowanie", "60kg", "8 8 6 7 5")
        TrainRow("Dipy", "10kg", "6 6 8 8 6")
    }
}

@Composable
fun TrainRow(col1: String, col2: String, col3: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(text = col1, weight = 2f)
        TableCell(text = col2, weight = 1f)
        TableCell(text = col3, weight = 1f)
    }
}

@Composable
fun RowScope.TableCell(text: String, weight: Float, isHeader: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp
    )
}

@Composable
fun BottomNav() {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { /* Nawigacja do Home */ },
            label = { Text("Panel") },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Panel") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Nawigacja do Trenera */ },
            label = { Text("Trener") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Trener") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Nawigacja do Profilu */ },
            label = { Text("Postep") },
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Nawigacja do Profilu */ },
            label = { Text("Konto") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Konto") }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    GymManagerTheme {
        MainContent()
    }
}