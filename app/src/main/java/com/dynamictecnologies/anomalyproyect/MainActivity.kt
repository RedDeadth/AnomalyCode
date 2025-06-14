package com.dynamictecnologies.anomalyproyect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dynamictecnologies.anomalyproyect.ui.theme.AnomalyProyectTheme
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnomalyProyectTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
@Composable
fun suma(num1: Int, num2: Int): Int {
    return num1 + num2
}

@Composable
fun resta(num1: Int, num2: Int): Int {
    return num1 - num2
}
@Composable
fun multiplicacion(num1: Int, num2: Int): Int {
    return num1 * num2
}
@Composable
fun division(num1: Int, num2: Int): Int {
    return num1 / num2
}
@Composable
fun potencia(num1: Int, num2: Int): Int {
    return num1.toDouble().pow(num2.toDouble()).toInt()
}