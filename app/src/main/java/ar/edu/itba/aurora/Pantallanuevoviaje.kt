package ar.edu.itba.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PantallaNuevoViaje(
    onVolver: () -> Unit,
    onIniciar: (String, String) -> Unit,
    guardando: Boolean = false,
    error: String? = null
) {
    var origen by remember { mutableStateOf("") }
    var destino by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            IconButton(
                onClick = onVolver,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Nuevo viaje",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 28.dp)
                    .width(24.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )

            CampoConEtiqueta("origen", origen, "Rosario") { origen = it }
            CampoConEtiqueta("destino", destino, "Tucuman") { destino = it }
            CampoConEtiqueta("unidad", unidad, "AB 123 CD") { unidad = it }

            // Estado del dispositivo
            Row(modifier = Modifier.padding(top = 12.dp, bottom = 26.dp)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(48.dp)
                        .background(VerdeOk)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("AURORA-01", style = DatoMono, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Conectado - bateria 78 %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                onClick = { onIniciar(origen, destino) },
                enabled = !guardando && origen.isNotBlank() && destino.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (guardando) "Iniciando..." else "Iniciar viaje")
            }

            Text(
                "Durante los primeros 5 minutos del viaje, el dispositivo calibra tu línea de base.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp, bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun CampoConEtiqueta(
    etiqueta: String,
    valor: String,
    ejemplo: String,
    onCambio: (String) -> Unit
) {
    Text(
        etiqueta,
        style = EtiquetaMono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        placeholder = { Text(ejemplo) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    )
}