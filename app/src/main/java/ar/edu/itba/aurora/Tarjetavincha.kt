package ar.edu.itba.aurora

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Contenido de la seccion "dispositivo" de Configuracion: estado de la
 * vincha y los botones para vincularla, reconectarla o desvincularla.
 *
 * Antes de conectar se asegura, en este orden, de que:
 *  1. el chofer dio el permiso de Bluetooth (se lo pide si falta),
 *  2. el Bluetooth esta prendido (le ofrece prenderlo),
 *  3. en Android 11 o anterior, la Ubicacion esta prendida (Android la exige
 *     para buscar dispositivos Bluetooth; la app no usa la ubicacion).
 */
@Composable
fun ControlVincha() {
    val contexto = LocalContext.current
    val estado by Vincha.estado.collectAsState()
    val vinculada by Vincha.vinculada.collectAsState()
    val aviso by Vincha.aviso.collectAsState()

    val prenderBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Vincha.bluetoothPrendido(contexto)) seguirVinculando(contexto, null)
        else Vincha.avisar("Hace falta prender el Bluetooth para conectar la vincha.")
    }

    val pedirPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { respuestas ->
        if (respuestas.values.all { it }) seguirVinculando(contexto, prenderBluetooth)
        else Vincha.avisar(
            "Sin el permiso de Bluetooth la app no puede conectarse a la vincha. " +
                    "Podés darlo desde Ajustes > Aplicaciones > Aurora > Permisos."
        )
    }

    // Sirve tanto para vincular por primera vez como para reconectar
    val alTocarVincular: () -> Unit = {
        Vincha.avisar(null)
        if (!Vincha.tienePermisos(contexto)) pedirPermisos.launch(Vincha.permisosNecesarios())
        else seguirVinculando(contexto, prenderBluetooth)
    }

    val (textoEstado, resaltado) = when {
        !vinculada -> "sin vincular" to false
        estado == EstadoVincha.CONECTADA -> "conectada" to false
        estado == EstadoVincha.CONECTANDO -> "conectando..." to false
        estado == EstadoVincha.BUSCANDO -> "buscando..." to false
        else -> "desconectada" to true
    }

    FilaVincha("Vincha", if (vinculada) "${Vincha.NOMBRE} · $textoEstado" else textoEstado, resaltado)
    // La vincha todavia no manda la bateria
    FilaVincha("Batería", "-")

    if (aviso != null) {
        Text(
            aviso ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    if (!vinculada) {
        Button(
            onClick = alTocarVincular,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        ) {
            Text("Vincular dispositivo")
        }
    } else {
        // Vinculada pero desconectada (Bluetooth apagado, sin permiso...):
        // ofrece reconectar a mano, ademas de desvincular.
        if (estado == EstadoVincha.DESCONECTADA) {
            Button(
                onClick = alTocarVincular,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text("Reconectar")
            }
            TextButton(
                onClick = { Vincha.desvincular() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desvincular")
            }
        } else {
            OutlinedButton(
                onClick = { Vincha.desvincular() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text("Desvincular")
            }
        }
    }
}

/** Misma forma que las filas del resto de Configuracion. */
@Composable
private fun FilaVincha(etiqueta: String, valor: String, resaltado: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            valor,
            style = DatoMono,
            color = if (resaltado) Nivel2 else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Pasos 2 y 3 de la vinculacion (el permiso ya esta dado).
 * prenderBluetooth = null cuando el chofer ya rechazo prenderlo una vez.
 */
@SuppressLint("MissingPermission")
private fun seguirVinculando(ctx: Context, prenderBluetooth: ActivityResultLauncher<Intent>?) {
    when {
        !Vincha.hayBluetooth(ctx) ->
            Vincha.avisar("Este teléfono no tiene Bluetooth.")
        !Vincha.bluetoothPrendido(ctx) ->
            if (prenderBluetooth != null) prenderBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else Vincha.avisar("Prendé el Bluetooth y volvé a intentar.")
        Vincha.faltaUbicacion(ctx) ->
            Vincha.avisar(
                "En este teléfono Android pide tener la Ubicación prendida para buscar " +
                        "dispositivos Bluetooth. Prendela y volvé a intentar."
            )
        else -> Vincha.vincular(ctx)
    }
}