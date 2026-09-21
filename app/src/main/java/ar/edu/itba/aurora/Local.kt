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

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Query("SELECT * FROM viajes WHERE choferId = :chofer ORDER BY inicioMs DESC")
    suspend fun viajesDe(chofer: String): List<ViajeLocal>

    @Query("SELECT * FROM eventos ORDER BY ocurridoEnMs DESC")
    suspend fun todosLosEventos(): List<EventoLocal>

    // ------------------------------------------------------------------
    // Escritura desde la app
    // ------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarViaje(viaje: ViajeLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEvento(evento: EventoLocal)

    /** Devuelve cuantas filas cambio: 0 si el viaje no estaba en el telefono. */
    @Query("UPDATE viajes SET finMs = :finMs, estado = 'finalizado', pendiente = 1 WHERE id = :id")
    suspend fun cerrarViaje(id: String, finMs: Long): Int

    // ------------------------------------------------------------------
    // Escritura desde la nube: NUNCA pisa lo que hay en el telefono
    // ------------------------------------------------------------------
    // Solo se usa para traer el viaje en curso si el telefono no lo tenia
    // (por ejemplo, si se reinstalo la app a mitad de un viaje). Si la fila
    // ya existe, se ignora: lo local siempre gana.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardarViajeSiNoExiste(viaje: ViajeLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardarEventosSiNoExisten(eventos: List<EventoLocal>)

    // ------------------------------------------------------------------
    // Lo que falta subir
    // ------------------------------------------------------------------

    @Query("SELECT * FROM viajes WHERE pendiente = 1")
    suspend fun viajesPendientes(): List<ViajeLocal>

    @Query("SELECT * FROM eventos WHERE pendiente = 1")
    suspend fun eventosPendientes(): List<EventoLocal>

    /**
     * Marca un viaje como subido SOLO si no cambio desde que se leyo para
     * subirlo. Si el chofer lo finalizo mientras se estaba subiendo, el
     * estado o la hora de fin ya no coinciden, la fila queda pendiente y se
     * sube en la vuelta siguiente. Sin esto, la finalizacion se perdia.
     *
     * finMsOMenosUno: la hora de fin que se subio, o -1 si no tenia.
     */
    @Query(
        "UPDATE viajes SET pendiente = 0 WHERE id = :id AND estado = :estado " +
            "AND IFNULL(finMs, -1) = :finMsOMenosUno"
    )
    suspend fun marcarViajeSubidoSiNoCambio(id: String, estado: String, finMsOMenosUno: Long)

    // Los eventos nunca se modifican despues de creados, asi que se pueden
    // marcar por id sin riesgo.
    @Query("UPDATE eventos SET pendiente = 0 WHERE id IN (:ids)")
    suspend fun marcarEventosSubidos(ids: List<String>)

    @Query(
        "SELECT (SELECT COUNT(*) FROM viajes WHERE pendiente = 1) + " +
            "(SELECT COUNT(*) FROM eventos WHERE pendiente = 1)"
    )
    suspend fun cuantosPendientes(): Int

    // ------------------------------------------------------------------
    // Limpieza: el telefono guarda solo lo que hace falta
    // ------------------------------------------------------------------
    // Se borra un viaje cuando ya esta subido, ya termino y no le queda
    // ningun evento sin subir. El viaje en curso se queda siempre, aunque
    // este subido: si se corta la señal, hay que poder finalizarlo.

    @Query(
        "DELETE FROM eventos WHERE pendiente = 0 AND viajeId IN " +
            "(SELECT id FROM viajes WHERE pendiente = 0 AND estado != 'en_curso')"
    )
    suspend fun borrarEventosDeViajesTerminados()

    @Query(
        "DELETE FROM viajes WHERE pendiente = 0 AND estado != 'en_curso' " +
            "AND id NOT IN (SELECT viajeId FROM eventos)"
    )
    suspend fun borrarViajesTerminados()
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
                // OJO: si en el futuro cambia la estructura de las tablas,
                // esto rehace la base y se pierde lo que no se subio. Antes
                // de cambiar una tabla hay que escribir una Migration.
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }

    val dao: AuroraDao
        get() = base?.dao() ?: error("La base local todavia no esta iniciada")
}