package ar.edu.itba.aurora

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Ingreso del chofer. Es la misma tarjeta del login de la web, sobre el mismo
 * cielo animado, en version dia y noche.
 */
@Composable
fun PantallaLogin(
    temaOscuro: Boolean = true,
    onCambiarTema: (Boolean) -> Unit = {},
    cargando: Boolean = false,
    error: String? = null,
    onOlvide: () -> Unit = {},
    onIngresar: (String, String) -> Unit
) {
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var verClave by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    EscenaLogin(temaOscuro = temaOscuro, onCambiarTema = onCambiarTema) {

        CampoLogin(
            oscuro = temaOscuro,
            etiqueta = "Usuario",
            valor = usuario,
            onCambio = { usuario = it; aviso = null },
            habilitado = !cargando
        )

        CampoLogin(
            oscuro = temaOscuro,
            etiqueta = "Contraseña",
            valor = clave,
            onCambio = { clave = it; aviso = null },
            habilitado = !cargando,
            transformacion =
                if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
            iconoFinal = {
                IconButton(onClick = { verClave = !verClave }) {
                    Icon(
                        imageVector =
                            if (verClave) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription =
                            if (verClave) "Ocultar contraseña" else "Ver contraseña",
                        tint = colorIconoCampo(temaOscuro)
                    )
                }
            }
        )

        // El aviso local (campos vacios) tiene prioridad sobre el error anterior
        val mensaje = aviso ?: error
        if (mensaje != null) MensajeLogin(mensaje, TipoMensaje.ERROR, temaOscuro)

        BotonLogin(
            texto = if (cargando) "Ingresando..." else "Ingresar",
            habilitado = !cargando,
            onClick = {
                if (usuario.isBlank() || clave.isBlank()) {
                    aviso = "Completá tu usuario y tu contraseña."
                } else {
                    aviso = null
                    onIngresar(usuario, clave)
                }
            }
        )

        EnlaceLogin("¿Olvidaste tu contraseña?", onClick = onOlvide, habilitado = !cargando)
    }
}
