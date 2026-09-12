package ar.edu.itba.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Cambio de contraseña. Se llega desde Configuracion y siempre se puede
 * volver con la flecha.
 */
@Composable
fun PantallaCambiarPassword(
    cargando: Boolean = false,
    error: String? = null,
    listo: Boolean = false,
    onVolver: () -> Unit = {},
    onGuardar: (String) -> Unit
) {
    var nueva by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }
    var verClave by remember { mutableStateOf(false) }

    val corta = nueva.length < 6
    val distintas = nueva.isNotEmpty() && repetida.isNotEmpty() && nueva != repetida

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
            IconButton(onClick = onVolver, modifier = Modifier.padding(top = 8.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Cambiar contraseña",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 20.dp)
                    .width(24.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )

            if (listo) {
                Text(
                    "Listo, tu contraseña quedo cambiada. La proxima vez que entres " +
                            "usa la nueva.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onVolver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp, bottom = 32.dp)
                ) {
                    Text("Volver a configuracion")
                }
            } else {
                Text(
                    "Elegi una contraseña nueva, de al menos 6 caracteres.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    "contraseña nueva",
                    style = EtiquetaMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = nueva,
                    onValueChange = { nueva = it },
                    singleLine = true,
                    enabled = !cargando,
                    visualTransformation =
                        if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { verClave = !verClave }) {
                            Icon(
                                imageVector =
                                    if (verClave) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                contentDescription =
                                    if (verClave) "Ocultar contraseña" else "Ver contraseña",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "repetila",
                    style = EtiquetaMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                )
                OutlinedTextField(
                    value = repetida,
                    onValueChange = { repetida = it },
                    singleLine = true,
                    enabled = !cargando,
                    isError = distintas,
                    visualTransformation =
                        if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                val aviso = when {
                    distintas -> "Las dos contraseñas no coinciden."
                    error != null -> error
                    else -> null
                }
                if (aviso != null) {
                    Text(
                        aviso,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                Button(
                    onClick = { onGuardar(nueva) },
                    enabled = !cargando && !corta && !distintas && repetida.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp, bottom = 32.dp)
                ) {
                    Text(if (cargando) "Guardando..." else "Guardar contraseña")
                }
            }
        }
    }
}