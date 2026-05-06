package biali.fitmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ProgressActivity : ComponentActivity() {
    private val viewModel: ProgressViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        viewModel.fetchProgress()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProgressScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {

    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        // Tytuł
        Text(
            text = "Twoje postępy",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A6B5D) // Zbliżony do zieleni z makiety
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sprawdzamy stan
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E676))
                }
            }
            state.error != null -> {
                Text(text = "Błąd: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
            state.progressData != null -> {
                val data = state.progressData!!

                Text(
                    text = "Twój pierwszy trening odbył się ${data.daysSinceFirstTraining} dni temu!",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Analiza wyników",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A6B5D)
                )

                Text(
                    text = data.dateRange,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Tabela (Nagłówki)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Nazwa ćwiczenia", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Ciężar", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                }

                // Wiersze z ćwiczeniami
                data.progressList.forEach { exercise ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = exercise.exerciseName, modifier = Modifier.weight(1f), color = Color.DarkGray)
                        Text(
                            text = "${exercise.startWeight}kg -> ${exercise.endWeight}kg",
                            modifier = Modifier.weight(0.5f),
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Miejsce na wykres (Prosta imitacja słupków z Compose)
                Row(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.chartData.forEach { value ->
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height((value * 1.5).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(Color(0xFF00E676))
                        )
                    }
                }
            }
        }
    }
}