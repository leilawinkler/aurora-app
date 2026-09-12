package ar.edu.itba.aurora

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Cada funcion de abajo dibuja una pantalla dentro de Android Studio,
// sin necesidad de tablet ni emulador.
//
// Para verlas: abri este archivo y arriba a la derecha del editor
// elegi la vista dividida (el icono del medio de los tres).
//
// Cambia "oscuro = true" por "oscuro = false" para ver el tema claro.

@Preview(name = "Login", showBackground = true, heightDp = 800)
@Composable
fun VistaPreviaLogin() {
    AuroraTheme(oscuro = true) {
        PantallaLogin(onIngresar = { _, _ -> })
    }
}

@Preview(name = "Viajes", showBackground = true, heightDp = 800)
@Composable
fun VistaPreviaViajes() {
    AuroraTheme(oscuro = true) {
        PantallaViajes(
            viajeEnCurso = DatosDeEjemplo.viajeEnCurso,
            viajes = DatosDeEjemplo.viajes,
            temaOscuro = true,
            onCambiarTema = {},
            onNuevoViaje = {},
            onAbrirViaje = {},
            onIrAConfiguracion = {},
            nombreChofer = "Ruben Gonzalez"
        )
    }
}

@Preview(name = "Viajes - tema claro", showBackground = true, heightDp = 800)
@Composable
fun VistaPreviaViajesClaro() {
    AuroraTheme(oscuro = false) {
        PantallaViajes(
            viajeEnCurso = DatosDeEjemplo.viajeEnCurso,
            viajes = DatosDeEjemplo.viajes,
            temaOscuro = false,
            onCambiarTema = {},
            onNuevoViaje = {},
            onAbrirViaje = {},
            onIrAConfiguracion = {},
            nombreChofer = "Ruben Gonzalez"
        )
    }
}

@Preview(name = "Nuevo viaje", showBackground = true, heightDp = 900)
@Composable
fun VistaPreviaNuevoViaje() {
    AuroraTheme(oscuro = true) {
        PantallaNuevoViaje(onVolver = {}, onIniciar = { _, _ -> })
    }
}

@Preview(name = "Detalle", showBackground = true, heightDp = 1100)
@Composable
fun VistaPreviaDetalle() {
    AuroraTheme(oscuro = true) {
        PantallaDetalle(viaje = DatosDeEjemplo.viajes[1], onVolver = {})
    }
}

@Preview(name = "Configuracion", showBackground = true, heightDp = 950)
@Composable
fun VistaPreviaConfiguracion() {
    AuroraTheme(oscuro = true) {
        PantallaConfiguracion(
            temaOscuro = true,
            onCambiarTema = {},
            onIrAViajes = {},
            onCerrarSesion = {},
            nombreChofer = "Ruben Gonzalez",
            usuario = "rgonzalez",
            empresa = "Transportes del Norte"
        )
    }
}

@Preview(name = "Cambiar contraseña", showBackground = true, heightDp = 800)
@Composable
fun VistaPreviaCambiarPassword() {
    AuroraTheme(oscuro = true) {
        PantallaCambiarPassword(onGuardar = {})
    }
}