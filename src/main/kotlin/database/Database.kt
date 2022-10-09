package database

import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.DaoManager
import com.j256.ormlite.jdbc.JdbcConnectionSource
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import config
import database.models.*

lateinit var connectionSource: ConnectionSource
lateinit var chapterDao: Dao<UserChapter, String>
lateinit var chapterEntryDao: Dao<ChapterEntry, String>
lateinit var sharedArtCacheEntryDao: Dao<SharedArtCacheEntry, String>
lateinit var userDao: Dao<User, String>
lateinit var thingyExtensionDao: Dao<ThingyExtension, String>

fun initDatabase() {
    val databaseUrl = "jdbc:sqlite:${config.databasePath}"
    // create a connection source to our database
    connectionSource = JdbcConnectionSource(databaseUrl)
    TableUtils.createTableIfNotExists(connectionSource, UserChapter::class.java)
    TableUtils.createTableIfNotExists(connectionSource, ChapterEntry::class.java)
    TableUtils.createTableIfNotExists(connectionSource, User::class.java)
    TableUtils.createTableIfNotExists(connectionSource, SharedArtCacheEntry::class.java)
    TableUtils.createTableIfNotExists(connectionSource, ThingyExtension::class.java)
    chapterEntryDao = DaoManager.createDao(connectionSource, ChapterEntry::class.java)
    chapterDao = DaoManager.createDao(connectionSource, UserChapter::class.java)
    userDao = DaoManager.createDao(connectionSource, User::class.java)
    sharedArtCacheEntryDao = DaoManager.createDao(connectionSource, SharedArtCacheEntry::class.java)
    thingyExtensionDao = DaoManager.createDao(connectionSource, ThingyExtension::class.java)
}