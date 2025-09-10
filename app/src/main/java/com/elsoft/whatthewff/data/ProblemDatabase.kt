// File: data/ProblemDatabase.kt
// This file contains the complete setup for the Room database, which will be used
// to store the user's custom imported problem sets.

package com.elsoft.whatthewff.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.elsoft.whatthewff.logic.Formula
import com.elsoft.whatthewff.logic.Proof
import com.elsoft.whatthewff.logic.WffParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    private val gson = Gson()

    @TypeConverter
    fun fromFormulaList(value: List<Formula>?): String? {
        return gson.toJson(value?.map { it.stringValue })
    }

    @TypeConverter
    fun toFormulaList(value: String?): List<Formula>? {
        val listType = object : TypeToken<List<String>>() {}.type
        val stringList: List<String>? = gson.fromJson(value, listType)
        return stringList?.mapNotNull { WffParser.f(it) }
    }

    @TypeConverter
    fun fromFormula(value: Formula?): String? {
        return value?.stringValue
    }

    @TypeConverter
    fun toFormula(value: String?): Formula? {
        return value?.let { WffParser.f(it) }
    }

    @TypeConverter
    fun fromProof(value: Proof?): String? {
        // TODO: Implement Converters.fromProof : Proof? -> String?
        // A more robust implementation would serialize the whole Proof object
        return null // Placeholder for now
    }

    @TypeConverter
    fun toProof(value: String?): Proof? {
        // TODO: Implement Converters.toProof : String? -> Proof?
        // A more robust implementation would deserialize the Proof object
        return null // Placeholder for now
    }
}

// --- DAO (Data Access Object) ---

@Dao
interface ProblemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblemSet(set: ProblemSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblems(problems: List<CustomProblemEntity>)

    @Query("SELECT * FROM problem_sets ORDER BY title ASC")
    suspend fun getAllProblemSets(): List<ProblemSetEntity>

    @Query("SELECT * FROM custom_problems WHERE problemSetTitle = :setTitle ORDER BY id ASC")
    suspend fun getProblemsForSet(setTitle: String): List<CustomProblemEntity>

    @Update
    suspend fun updateProblem(problem: CustomProblemEntity)

    @Query("DELETE FROM problem_sets WHERE title = :setTitle")
    suspend fun deleteProblemSet(setTitle: String)
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
