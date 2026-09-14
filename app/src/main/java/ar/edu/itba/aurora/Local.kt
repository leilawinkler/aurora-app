package ar.edu.itba.aurora

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// ============================================================================
// La base de datos que vive adentro del telefono
// ============================================================================
// La app escribe y lee SIEMPRE de aca, tenga internet o no. Cada fila tiene
// una marca "pendiente": si esta en true, todavia no se subio a Supabase.
// El sincronizador (ver Almacen.kt) se encarga de subir lo pendiente cuando
// hay conexion y de apagar esa marca.

@Entity(tableName = "viajes")
data class ViajeLocal(
    @PrimaryKey val id: String,
    val empresaId: String,
    val choferId: String,
    val inicioMs: Long,
    val finMs: Long?,
    val estado: String,
    val origen: String,
    val destino: String,
    val pendiente: Boolean
)

@Entity(tableName = "eventos")
data class EventoLocal(
    @PrimaryKey val id: String,
    val viajeId: String,
    val empresaId: String,
    val tipo: String,
    val nivel: Int,
    val ocurridoEnMs: Long,
    val valor: Double?,
    val pendiente: Boolean
)

// ============================================================================
// Las operaciones disponibles sobre esa base
// ============================================================================
// Room escribe solo el codigo SQL a partir de estas anotaciones.

@Dao
interface AuroraDao {

    @Query("SELECT * FROM viajes WHERE choferId = :chofer ORDER BY inicioMs DESC")
    suspend fun viajesDe(chofer: String): List<ViajeLocal>

    @Query("SELECT * FROM eventos ORDER BY ocurridoEnMs DESC")
    suspend fun todosLosEventos(): List<EventoLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarViaje(viaje: ViajeLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarViajes(viajes: List<ViajeLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEvento(evento: EventoLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEventos(eventos: List<EventoLocal>)

    @Query("UPDATE viajes SET finMs = :finMs, estado = 'finalizado', pendiente = 1 WHERE id = :id")
    suspend fun cerrarViaje(id: String, finMs: Long)

    // --- Lo que falta subir ---

    @Query("SELECT * FROM viajes WHERE pendiente = 1")
    suspend fun viajesPendientes(): List<ViajeLocal>

    @Query("SELECT * FROM eventos WHERE pendiente = 1")
    suspend fun eventosPendientes(): List<EventoLocal>

    @Query("UPDATE viajes SET pendiente = 0 WHERE id IN (:ids)")
    suspend fun marcarViajesSubidos(ids: List<String>)

    @Query("UPDATE eventos SET pendiente = 0 WHERE id IN (:ids)")
    suspend fun marcarEventosSubidos(ids: List<String>)

    @Query(
        "SELECT (SELECT COUNT(*) FROM viajes WHERE pendiente = 1) + " +
                "(SELECT COUNT(*) FROM eventos WHERE pendiente = 1)"
    )
    suspend fun cuantosPendientes(): Int

    // --- Limpieza al cerrar sesion (solo lo ya subido) ---

    @Query("DELETE FROM eventos WHERE pendiente = 0")
    suspend fun borrarEventosSubidos()

    @Query("DELETE FROM viajes WHERE pendiente = 0")
    suspend fun borrarViajesSubidos()
}

@Database(
    entities = [ViajeLocal::class, EventoLocal::class],
    version = 1,
    exportSchema = false
)
abstract class BaseLocal : RoomDatabase() {
    abstract fun dao(): AuroraDao
}

/**
 * Punto de acceso unico a la base local.
 * Se prende una sola vez, al arrancar la app (ver MainActivity).
 */
object Local {
    private var base: BaseLocal? = null

    fun iniciar(contexto: Context) {
        if (base == null) {
            base = Room.databaseBuilder(
                contexto.applicationContext,
                BaseLocal::class.java,
                "aurora.db"
            )
                // Si en el futuro cambia la estructura, se rehace la base
                // local en vez de romper. Los datos viven en Supabase.
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }

    val dao: AuroraDao
        get() = base?.dao() ?: error("La base local todavia no esta iniciada")
}