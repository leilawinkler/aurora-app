package ar.edu.itba.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * "Olvide mi contraseña". El chofer escribe su usuario y su mail de contacto;
 * si coinciden, le llega por mail una contraseña temporal nueva.
 *
 * Por seguridad el mensaje de exito es siempre el mismo, coincidan o no los
 * datos (asi nadie puede averiguar que usuarios existen).
 */
@Composable
fun PantallaRecuperar(
    usuarioInicial: String = "",
    cargando: Boolean = false,
    mensajeOk: String? = null,
    error: String? = null,
    onEnviar: (usuario: String, mail: String) -> Unit,
    onVolver: () -> Unit
) {
    var usuario by remember { mutableStateOf(usuarioInicial) }
    var mail by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("aurora", style = Marca, color = MaterialTheme.colorScheme.onBackground)

            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .width(34.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Text(
                text = "Recuperar contraseña",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 24.dp)
            )

            if (mensajeOk != null) {
                // --- Pedido enviado ---
                Text(
                    text = mensajeOk,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Button(
                    onClick = onVolver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    Text("Volver al ingreso")
                }
            } else {
                // --- Formulario ---
                Text(
                    text = "Escribi tu usuario y el mail con el que te dieron de alta. " +
                            "Si coinciden, te mandamos una contraseña temporal nueva.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, bottom = 32.dp)
                )

                Text(
                    text = "Usuario",
                    style = EtiquetaMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    singleLine = true,
                    enabled = !cargando,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Mail",
                    style = EtiquetaMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 6.dp)
                )
                OutlinedTextField(
                    value = mail,
                    onValueChange = { mail = it },
                    singleLine = true,
                    enabled = !cargando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    )
                }

                Button(
                    onClick = { onEnviar(usuario, mail) },
                    enabled = !cargando && usuario.isNotBlank() && mail.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    Text(if (cargando) "Enviando..." else "Enviar contraseña nueva")
                }

                TextButton(
                    onClick = onVolver,
                    enabled = !cargando,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Volver al ingreso")
                }
            }
        }
    }
}