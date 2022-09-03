package database

import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.DaoManager
import com.j256.ormlite.jdbc.JdbcConnectionSource
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import database.models.User
import database.models.UserChapter

lateinit var connectionSource: ConnectionSource
lateinit var chapterDao: Dao<UserChapter, String>
lateinit var userDao: Dao<User, String>

fun initDatabase() {
    val databaseUrl = "jdbc:sqlite:db.sqlite"
    // create a connection source to our database
    connectionSource = JdbcConnectionSource(databaseUrl)
    TableUtils.createTableIfNotExists(connectionSource, UserChapter::class.java)
    TableUtils.createTableIfNotExists(connectionSource, User::class.java)
    chapterDao = DaoManager.createDao(connectionSource, UserChapter::class.java)
    userDao = DaoManager.createDao(connectionSource, User::class.java)
}