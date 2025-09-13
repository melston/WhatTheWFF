// File: data/ProblemDatabase.kt
// This file contains the complete setup for the Room database, which will be used
// to store the user's custom imported problem sets.

package com.elsoft.whatthewff.data

import android.content.Context
import androidx.room.*
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.Justification
import com.elsoft.whatthewff.logic.LogicTile
import com.elsoft.whatthewff.logic.Proof
import com.elsoft.whatthewff.logic.SymbolType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.JsonParseException
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.internal.Streams
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.flow.Flow
import java.io.IOException

// --- Database Entities (Tables) ---

@Entity(tableName = "problem_sets")
data class ProblemSetEntity(
    @PrimaryKey val title: String
)

@Entity(
    tableName = "custom_problems",
    primaryKeys = ["id", "problemSetTitle"],
    foreignKeys = [ForeignKey(
        entity = ProblemSetEntity::class,
        parentColumns = ["title"],
        childColumns = ["problemSetTitle"],
        onDelete = ForeignKey.CASCADE // If a set is deleted, delete its problems
    )]
)
data class CustomProblemEntity(
    val id: String,
    val problemSetTitle: String,
    val premises: List<Formula>,
    val conclusion: Formula,
    val solvedProof: Proof?
)

// --- Type Converters for Custom Objects ---

/**
 * Teaches Room how to store our custom Formula and Proof objects.
 * It converts them to and from a JSON string for storage in the database.
 */
class Converters {
    private val gson: Gson by lazy {
        // Create a special Type Adapter Factory for our Justification sealed class
        val justificationAdapterFactory = RuntimeTypeAdapterFactory.of(
            Justification::class.java, "type")
            .registerSubtype(Justification.Premise::class.java, "premise")
            .registerSubtype(Justification.Assumption::class.java, "assumption")
            .registerSubtype(Justification.Inference::class.java, "inference")
            .registerSubtype(Justification.Replacement::class.java, "replacement")
            .registerSubtype(Justification.ImplicationIntroduction::class.java, "implication_introduction")
            .registerSubtype(Justification.ReductioAdAbsurdum::class.java, "reductio_ad_absurdum")
            .registerSubtype(Justification.Reiteration::class.java, "reiteration")

        // Build a new Gson instance with our custom adapter
        GsonBuilder()
            .registerTypeAdapter(LogicTile::class.java, LogicTileAdapter()) // <-- Register new adapter
            .registerTypeAdapterFactory(justificationAdapterFactory)
            .create()
    }

    @TypeConverter
    fun fromFormula(value: Formula?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFormula(value: String?): Formula? {
        return gson.fromJson(value, Formula::class.java)
    }

    @TypeConverter
    fun fromFormulaList(value: List<Formula>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFormulaList(value: String?): List<Formula>? {
        val listType = object : TypeToken<List<Formula>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromProof(value: Proof?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toProof(value: String?): Proof? {
        return value?.let { gson.fromJson(it, Proof::class.java) }
    }
}

// --- DAO (Data Access Object) ---

@Dao
interface ProblemDao {
    @Query("SELECT * FROM problem_sets ORDER BY title ASC")
    suspend fun getAllProblemSets(): List<ProblemSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblemSet(set: ProblemSetEntity)

    @Query("DELETE FROM problem_sets WHERE title = :setTitle")
    suspend fun deleteProblemSet(setTitle: String)

    @Query("SELECT * FROM custom_problems WHERE problemSetTitle = :setTitle ORDER BY id ASC")
    fun getProblemsForSet(setTitle: String): Flow<List<CustomProblemEntity>>

    @Query("SELECT * FROM custom_problems WHERE problemSetTitle = :setTitle AND id = :problemId LIMIT 1")
    suspend fun getProblemEntity(setTitle: String, problemId: String): CustomProblemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblems(problems: List<CustomProblemEntity>)

    @Update
    suspend fun updateProblem(problem: CustomProblemEntity)
}


// --- Database Class ---

@Database(entities = [ProblemSetEntity::class, CustomProblemEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class ProblemDatabase : RoomDatabase() {
    abstract fun problemDao(): ProblemDao

    companion object {
        @Volatile
        private var INSTANCE: ProblemDatabase? = null

        fun getDatabase(context: Context): ProblemDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProblemDatabase::class.java,
                    "problem_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LogicTileAdapter : TypeAdapter<LogicTile>() {
    override fun write(out: JsonWriter, value: LogicTile?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("symbol").value(value.symbol)
        out.name("type").value(value.type.name)
        out.endObject()
    }

    override fun read(reader: JsonReader): LogicTile? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        reader.beginObject()
        var symbol: String? = null
        var type: SymbolType? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "symbol" -> symbol = reader.nextString()
                "type" -> type = SymbolType.valueOf(reader.nextString())
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if (symbol != null && type != null) LogicTile(symbol, type) else null
    }
}


// --- GSON Utility: RuntimeTypeAdapterFactory ---
// This is a standard utility class for helping Gson handle polymorphic types like sealed classes.
// No need to modify this, just include it in the file.
class RuntimeTypeAdapterFactory<T> private constructor(
    private val baseType: Class<*>,
    private val typeFieldName: String
) : com.google.gson.TypeAdapterFactory {
    private val labelToSubtype = LinkedHashMap<String, Class<*>>()
    private val subtypeToLabel = LinkedHashMap<Class<*>, String>()

    companion object {
        fun <T> of(baseType: Class<T>, typeFieldName: String): RuntimeTypeAdapterFactory<T> {
            return RuntimeTypeAdapterFactory(baseType, typeFieldName)
        }
    }

    fun registerSubtype(subtype: Class<out T>, label: String): RuntimeTypeAdapterFactory<T> {
        if (subtypeToLabel.containsKey(subtype) || labelToSubtype.containsKey(label)) {
            throw IllegalArgumentException("types and labels must be unique")
        }
        labelToSubtype[label] = subtype
        subtypeToLabel[subtype] = label
        return this
    }

    override fun <R : Any> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
        if (type.rawType != baseType) {
            return null
        }

        val labelToDelegate = LinkedHashMap<String, TypeAdapter<*>>()
        val subtypeToDelegate = LinkedHashMap<Class<*>, TypeAdapter<*>>()
        for ((label, subtype) in labelToSubtype) {
            val delegate = gson.getDelegateAdapter(this, TypeToken.get(subtype))
            labelToDelegate[label] = delegate
            subtypeToDelegate[subtype] = delegate
        }

        return object : TypeAdapter<R>() {
            @Throws(IOException::class)
            override fun read(inReader: JsonReader): R? {
                val jsonElement = Streams.parse(inReader)
                val labelJsonElement = jsonElement.asJsonObject.remove(typeFieldName)
                    ?: throw JsonParseException("cannot deserialize ${baseType} because it does not define a field named ${typeFieldName}")
                val label = labelJsonElement.asString
                val delegate = labelToDelegate[label] as TypeAdapter<R>?
                    ?: throw JsonParseException("cannot deserialize ${baseType} subtype named ${label}; did you forget to register a subtype?")
                return delegate.fromJsonTree(jsonElement)
            }

            @Throws(IOException::class)
            override fun write(out: JsonWriter, value: R) {
                val srcType = value.javaClass
                val label = subtypeToLabel[srcType]
                val delegate = subtypeToDelegate[srcType] as TypeAdapter<R>?
                    ?: throw JsonParseException("cannot serialize ${srcType.name}; did you forget to register a subtype?")
                val jsonObject = delegate.toJsonTree(value).asJsonObject
                if (jsonObject.has(typeFieldName)) {
                    throw JsonParseException("cannot serialize ${srcType.name} because it already defines a field named ${typeFieldName}")
                }
                val clone = JsonObject()
                clone.add(typeFieldName, gson.toJsonTree(label))
                for ((key, value1) in jsonObject.entrySet()) {
                    clone.add(key, value1)
                }
                Streams.write(clone, out)
            }
        }.nullSafe()
    }
}
