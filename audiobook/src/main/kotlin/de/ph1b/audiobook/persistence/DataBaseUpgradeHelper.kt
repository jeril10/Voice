package de.ph1b.audiobook.persistence

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import com.google.common.base.Charsets
import com.google.common.io.Files
import de.ph1b.audiobook.utils.App
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*


internal class DataBaseUpgradeHelper(private val db: SQLiteDatabase) {

    /**
     * Drops all tables and creates new ones.
     */
    private fun upgrade23() {
        db.execSQL("DROP TABLE IF EXISTS TABLE_BOOK")
        db.execSQL("DROP TABLE IF EXISTS TABLE_CHAPTERS")

        db.execSQL("CREATE TABLE TABLE_BOOK ( BOOK_ID INTEGER PRIMARY KEY AUTOINCREMENT, BOOK_TYPE TEXT NOT NULL, BOOK_ROOT TEXT NOT NULL)")
        db.execSQL("CREATE TABLE TABLE_CHAPTERS ( CHAPTER_ID INTEGER PRIMARY KEY AUTOINCREMENT, CHAPTER_PATH TEXT NOT NULL, CHAPTER_DURATION INTEGER NOT NULL, CHAPTER_NAME TEXT NOT NULL, BOOK_ID INTEGER NOT NULL, FOREIGN KEY(BOOK_ID) REFERENCES TABLE_BOOK(BOOK_ID))")
    }

    /**
     * Migrate the database so they will be stored as json objects

     * @throws InvalidPropertiesFormatException if there is an internal data mismatch
     */
    @SuppressWarnings("ConstantConditions", "CollectionWithoutInitialCapacity")
    @Throws(InvalidPropertiesFormatException::class)
    private fun upgrade24() {
        val copyBookTableName = "TABLE_BOOK_COPY"
        val copyChapterTableName = "TABLE_CHAPTERS_COPY"

        db.execSQL("ALTER TABLE TABLE_BOOK RENAME TO $copyBookTableName")
        db.execSQL("ALTER TABLE TABLE_CHAPTERS RENAME TO $copyChapterTableName")

        val newBookTable = "TABLE_BOOK"
        val CREATE_TABLE_BOOK = "CREATE TABLE $newBookTable ( BOOK_ID INTEGER PRIMARY KEY AUTOINCREMENT, BOOK_JSON TEXT NOT NULL)"
        db.execSQL(CREATE_TABLE_BOOK)


        val bookCursor = db.query(copyBookTableName,
                arrayOf("BOOK_ID", "BOOK_ROOT", "BOOK_TYPE"),
                null, null, null, null, null)
        try {
            while (bookCursor.moveToNext()) {
                val bookId = bookCursor.getLong(0)
                val root = bookCursor.getString(1)
                val type = bookCursor.getString(2)

                val mediaCursor = db.query(copyChapterTableName, arrayOf("CHAPTER_PATH", "CHAPTER_DURATION", "CHAPTER_NAME"),
                        "BOOK_ID" + "=?", arrayOf(bookId.toString()),
                        null, null, null)
                val chapterNames = ArrayList<String>(mediaCursor.count)
                val chapterDurations = ArrayList<Int>(mediaCursor.count)
                val chapterPaths = ArrayList<String>(mediaCursor.count)
                try {
                    while (mediaCursor.moveToNext()) {
                        chapterPaths.add(mediaCursor.getString(0))
                        chapterDurations.add(mediaCursor.getInt(1))
                        chapterNames.add(mediaCursor.getString(2))
                    }
                } finally {
                    mediaCursor.close()
                }

                val configFile: File
                when (type) {
                    "COLLECTION_FILE", "SINGLE_FILE" -> configFile = File(root, "." + chapterNames[0] + "-map.json")
                    "COLLECTION_FOLDER", "SINGLE_FOLDER" -> configFile = File(root, "." + (File(root).name) + "-map.json")
                    else -> throw InvalidPropertiesFormatException("Upgrade failed due to unknown type=" + type)
                }
                val backupFile = File(configFile.absolutePath + ".backup")

                val configFileValid = configFile.exists() && configFile.canRead() && configFile.length() > 0
                val backupFileValid = backupFile.exists() && backupFile.canRead() && backupFile.length() > 0


                var playingInformation: JSONObject? = null
                try {
                    if (configFileValid) {
                        val retString = Files.toString(configFile, Charsets.UTF_8)
                        if (!retString.isEmpty()) {
                            playingInformation = JSONObject(retString)
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                try {
                    if (playingInformation == null && backupFileValid) {
                        val retString = Files.toString(backupFile, Charsets.UTF_8)
                        playingInformation = JSONObject(retString)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                if (playingInformation == null) {
                    throw InvalidPropertiesFormatException("Could not fetch information")
                }

                val JSON_TIME = "time"
                val JSON_BOOKMARK_TIME = "time"
                val JSON_BOOKMARK_TITLE = "title"
                val JSON_SPEED = "speed"
                val JSON_NAME = "name"
                val JSON_BOOKMARKS = "bookmarks"
                val JSON_REL_PATH = "relPath"
                val JSON_BOOKMARK_REL_PATH = "relPath"
                val JSON_USE_COVER_REPLACEMENT = "useCoverReplacement"

                var currentTime = 0
                try {
                    currentTime = playingInformation.getInt(JSON_TIME)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                val bookmarkRelPathsUnsafe = ArrayList<String>()
                val bookmarkTitlesUnsafe = ArrayList<String>()
                val bookmarkTimesUnsafe = ArrayList<Int>()
                try {
                    val bookmarksJ = playingInformation.getJSONArray(JSON_BOOKMARKS)
                    for (i in 0..bookmarksJ.length() - 1) {
                        val bookmarkJ = bookmarksJ.get(i) as JSONObject
                        bookmarkTimesUnsafe.add(bookmarkJ.getInt(JSON_BOOKMARK_TIME))
                        bookmarkTitlesUnsafe.add(bookmarkJ.getString(JSON_BOOKMARK_TITLE))
                        bookmarkRelPathsUnsafe.add(bookmarkJ.getString(JSON_BOOKMARK_REL_PATH))
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    bookmarkRelPathsUnsafe.clear()
                    bookmarkTitlesUnsafe.clear()
                    bookmarkTimesUnsafe.clear()
                }

                val bookmarkRelPathsSafe = ArrayList<String>()
                val bookmarkTitlesSafe = ArrayList<String>()
                val bookmarkTimesSafe = ArrayList<Int>()

                for (i in bookmarkRelPathsUnsafe.indices) {
                    var bookmarkExists = false
                    for (chapterPath in chapterPaths) {
                        if (chapterPath == bookmarkRelPathsUnsafe[i]) {
                            bookmarkExists = true
                            break
                        }
                    }
                    if (bookmarkExists) {
                        bookmarkRelPathsSafe.add(bookmarkRelPathsUnsafe[i])
                        bookmarkTitlesSafe.add(bookmarkTitlesUnsafe[i])
                        bookmarkTimesSafe.add(bookmarkTimesUnsafe[i])
                    }
                }

                var currentPath = ""
                try {
                    currentPath = playingInformation.getString(JSON_REL_PATH)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                var relPathExists = false
                for (chapterPath in chapterPaths) {
                    if (chapterPath == currentPath) {
                        relPathExists = true
                    }
                }
                if (!relPathExists) {
                    currentPath = chapterPaths[0]
                    currentTime = 0
                }

                var speed = 1.0f
                try {
                    speed = java.lang.Float.valueOf(playingInformation.getString(JSON_SPEED))
                } catch (e: JSONException) {
                    e.printStackTrace()
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                }

                var name = ""
                try {
                    name = playingInformation.getString(JSON_NAME)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                if (name.isEmpty()) {
                    if (chapterPaths.size == 1) {
                        val chapterPath = chapterPaths[0]
                        name = chapterPath.substring(0, chapterPath.lastIndexOf("."))
                    } else {
                        name = File(root).name
                    }
                }

                var useCoverReplacement = false
                try {
                    useCoverReplacement = playingInformation.getBoolean(JSON_USE_COVER_REPLACEMENT)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                try {
                    val chapters = JSONArray()
                    for (i in chapterPaths.indices) {
                        val chapter = JSONObject()
                        chapter.put("path", root + File.separator + chapterPaths[i])
                        chapter.put("duration", chapterDurations[i])
                        chapters.put(chapter)
                    }

                    val bookmarks = JSONArray()
                    for (i in bookmarkRelPathsSafe.indices) {
                        val bookmark = JSONObject()
                        bookmark.put("mediaPath", root + File.separator + bookmarkRelPathsSafe[i])
                        bookmark.put("title", bookmarkTitlesSafe[i])
                        bookmark.put("time", bookmarkTimesSafe[i])
                        bookmarks.put(bookmark)
                    }

                    val book = JSONObject()
                    book.put("root", root)
                    book.put("name", name)
                    book.put("chapters", chapters)
                    book.put("currentMediaPath", root + File.separator + currentPath)
                    book.put("type", type)
                    book.put("bookmarks", bookmarks)
                    book.put("useCoverReplacement", useCoverReplacement)
                    book.put("time", currentTime)
                    book.put("playbackSpeed", speed.toDouble())

                    Timber.d("upgrade24 restored book=%s", book)
                    val cv = ContentValues()
                    cv.put("BOOK_JSON", book.toString())
                    val newBookId = db.insert(newBookTable, null, cv)
                    book.put("id", newBookId)


                    // move cover file if possible
                    val coverFile: File
                    if (chapterPaths.size == 1) {
                        val fileName = "." + chapterNames[0] + ".jpg"
                        coverFile = File(root, fileName)
                    } else {
                        val fileName = "." + (File(root).name) + ".jpg"
                        coverFile = File(root, fileName)
                    }
                    if (coverFile.exists() && coverFile.canWrite()) {
                        try {
                            val newCoverFile = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + "Android" + File.separator + "data" + File.separator + App.getComponent().context.packageName,
                                    newBookId.toString() + ".jpg")
                            if (!coverFile.parentFile.exists()) {
                                //noinspection ResultOfMethodCallIgnored
                                coverFile.parentFile.mkdirs()
                            }
                            Files.move(coverFile, newCoverFile)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }

                    }
                } catch (e: JSONException) {
                    throw InvalidPropertiesFormatException(e)
                }

            }
        } finally {
            bookCursor.close()
        }
    }


    /**
     * A previous version caused empty books to be added. So we delete them now.
     */
    @Throws(InvalidPropertiesFormatException::class)
    private fun upgrade25() {

        // get all books
        val cursor = db.query("TABLE_BOOK",
                arrayOf("BOOK_ID", "BOOK_JSON"),
                null, null, null, null, null)
        val allBooks = ArrayList<JSONObject>(cursor.count)
        try {
            while (cursor.moveToNext()) {
                val content = cursor.getString(1)
                val book = JSONObject(content)
                book.put("id", cursor.getLong(0))
                allBooks.add(book)
            }
        } catch (e: JSONException) {
            throw InvalidPropertiesFormatException(e)
        } finally {
            cursor.close()
        }

        // delete empty books
        try {
            for (b in allBooks) {
                val chapters = b.getJSONArray("chapters")
                if (chapters.length() == 0) {
                    db.delete("TABLE_BOOK", "BOOK_ID" + "=?", arrayOf(b.get("id").toString()))
                }
            }
        } catch (e: JSONException) {
            throw InvalidPropertiesFormatException(e)
        }

    }


    /**
     * Adds a new column indicating if the book should be actively shown or hidden.
     */
    private fun upgrade26() {
        val copyBookTableName = "TABLE_BOOK_COPY"
        db.execSQL("DROP TABLE IF EXISTS " + copyBookTableName)
        db.execSQL("ALTER TABLE TABLE_BOOK RENAME TO " + copyBookTableName)
        db.execSQL("CREATE TABLE " + "TABLE_BOOK" + " ( " + "BOOK_ID" + " INTEGER PRIMARY KEY AUTOINCREMENT, " + "BOOK_JSON" + " TEXT NOT NULL, " + "LAST_TIME_BOOK_WAS_ACTIVE" + " INTEGER NOT NULL, " + "BOOK_ACTIVE" + " INTEGER NOT NULL)")

        val cursor = db.query(copyBookTableName, arrayOf("BOOK_JSON"), null, null, null, null, null)
        cursor.use {
            while (cursor.moveToNext()) {
                val cv = ContentValues()
                cv.put("BOOK_JSON", cursor.getString(0))
                cv.put("BOOK_ACTIVE", 1)
                cv.put("LAST_TIME_BOOK_WAS_ACTIVE", System.currentTimeMillis())
                db.insert("TABLE_BOOK", null, cv)
            }
        }
    }


    /**
     * Deletes the table if that failed previously due to a bug in [.upgrade26]
     */
    private fun upgrade27() {
        db.execSQL("DROP TABLE IF EXISTS TABLE_BOOK_COPY")
    }


    /**
     * Adds

     * @throws InvalidPropertiesFormatException
     */
    @Throws(InvalidPropertiesFormatException::class)
    private fun upgrade28() {
        Timber.d("upgrade28")
        val cursor = db.query("TABLE_BOOK", arrayOf("BOOK_JSON", "BOOK_ID"), null, null, null, null, null)
        try {
            while (cursor.moveToNext()) {
                val book = JSONObject(cursor.getString(0))
                val chapters = book.getJSONArray("chapters")
                for (i in 0..chapters.length() - 1) {
                    val chapter = chapters.getJSONObject(i)
                    val fileName = File(chapter.getString("path")).name
                    val dotIndex = fileName.lastIndexOf(".")
                    val chapterName: String
                    if (dotIndex > 0) {
                        chapterName = fileName.substring(0, dotIndex)
                    } else {
                        chapterName = fileName
                    }
                    chapter.put("name", chapterName)
                }
                val cv = ContentValues()
                Timber.d("so saving book=%s", book.toString())
                cv.put("BOOK_JSON", book.toString())
                db.update("TABLE_BOOK", cv, "BOOK_ID" + "=?", arrayOf(cursor.getLong(1).toString()))
            }
        } catch (e: JSONException) {
            throw InvalidPropertiesFormatException(e)
        } finally {
            cursor.close()
        }
    }

    private fun upgrade29() {
        Timber.d("upgrade29")

        // fetching old contents
        val cursor = db.query("TABLE_BOOK", arrayOf("BOOK_JSON", "BOOK_ACTIVE"),
                null, null, null, null, null)
        val bookContents = ArrayList<String>(cursor.count)
        val activeMapping = ArrayList<Boolean>(cursor.count)
        cursor.use {
            while (cursor.moveToNext()) {
                bookContents.add(cursor.getString(0))
                activeMapping.add(cursor.getInt(1) == 1)
            }
        }
        db.execSQL("DROP TABLE TABLE_BOOK")

        // tables
        val TABLE_BOOK = "tableBooks"
        val TABLE_CHAPTERS = "tableChapters"
        val TABLE_BOOKMARKS = "tableBookmarks"

        // book keys
        val BOOK_ID = "bookId"
        val BOOK_NAME = "bookName"
        val BOOK_AUTHOR = "bookAuthor"
        val BOOK_CURRENT_MEDIA_PATH = "bookCurrentMediaPath"
        val BOOK_PLAYBACK_SPEED = "bookSpeed"
        val BOOK_ROOT = "bookRoot"
        val BOOK_TIME = "bookTime"
        val BOOK_TYPE = "bookType"
        val BOOK_USE_COVER_REPLACEMENT = "bookUseCoverReplacement"
        val BOOK_ACTIVE = "BOOK_ACTIVE"

        // chapter keys
        val CHAPTER_DURATION = "chapterDuration"
        val CHAPTER_NAME = "chapterName"
        val CHAPTER_PATH = "chapterPath"

        // bookmark keys
        val BOOKMARK_TIME = "bookmarkTime"
        val BOOKMARK_PATH = "bookmarkPath"
        val BOOKMARK_TITLE = "bookmarkTitle"

        // create strings
        val CREATE_TABLE_BOOK = "CREATE TABLE $TABLE_BOOK ( $BOOK_ID INTEGER PRIMARY KEY AUTOINCREMENT, $BOOK_NAME TEXT NOT NULL, $BOOK_AUTHOR TEXT, $BOOK_CURRENT_MEDIA_PATH TEXT NOT NULL, $BOOK_PLAYBACK_SPEED REAL NOT NULL, $BOOK_ROOT TEXT NOT NULL, $BOOK_TIME INTEGER NOT NULL, $BOOK_TYPE TEXT NOT NULL, $BOOK_USE_COVER_REPLACEMENT INTEGER NOT NULL, $BOOK_ACTIVE INTEGER NOT NULL DEFAULT 1)"

        val CREATE_TABLE_CHAPTERS = "CREATE TABLE $TABLE_CHAPTERS ( $CHAPTER_DURATION INTEGER NOT NULL, $CHAPTER_NAME TEXT NOT NULL, $CHAPTER_PATH TEXT NOT NULL, $BOOK_ID INTEGER NOT NULL, FOREIGN KEY ($BOOK_ID) REFERENCES $TABLE_BOOK($BOOK_ID))"

        val CREATE_TABLE_BOOKMARKS = "CREATE TABLE $TABLE_BOOKMARKS ( $BOOKMARK_PATH TEXT NOT NULL, $BOOKMARK_TITLE TEXT NOT NULL, $BOOKMARK_TIME INTEGER NOT NULL, $BOOK_ID INTEGER NOT NULL, FOREIGN KEY ($BOOK_ID) REFERENCES $TABLE_BOOK($BOOK_ID))"

        // drop tables in case they exist
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOK)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAPTERS)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS)

        // create new tables
        db.execSQL(CREATE_TABLE_BOOK)
        db.execSQL(CREATE_TABLE_CHAPTERS)
        db.execSQL(CREATE_TABLE_BOOKMARKS)

        for (i in bookContents.indices) {
            val bookJson = bookContents[i]
            val bookActive = activeMapping[i]

            try {
                val bookObj = JSONObject(bookJson)
                val bookmarks = bookObj.getJSONArray("bookmarks")
                val chapters = bookObj.getJSONArray("chapters")
                val currentMediaPath = bookObj.getString("currentMediaPath")
                val bookName = bookObj.getString("name")
                val speed = bookObj.getDouble("playbackSpeed").toFloat()
                val root = bookObj.getString("root")
                val time = bookObj.getInt("time")
                val type = bookObj.getString("type")
                val useCoverReplacement = bookObj.getBoolean("useCoverReplacement")

                val bookCV = ContentValues()
                bookCV.put(BOOK_CURRENT_MEDIA_PATH, currentMediaPath)
                bookCV.put(BOOK_NAME, bookName)
                bookCV.put(BOOK_PLAYBACK_SPEED, speed)
                bookCV.put(BOOK_ROOT, root)
                bookCV.put(BOOK_TIME, time)
                bookCV.put(BOOK_TYPE, type)
                bookCV.put(BOOK_USE_COVER_REPLACEMENT, if (useCoverReplacement) 1 else 0)
                bookCV.put(BOOK_ACTIVE, if (bookActive) 1 else 0)

                val bookId = db.insert(TABLE_BOOK, null, bookCV)


                for (j in 0..chapters.length() - 1) {
                    val chapter = chapters.getJSONObject(j)
                    val chapterDuration = chapter.getInt("duration")
                    val chapterName = chapter.getString("name")
                    val chapterPath = chapter.getString("path")

                    val chapterCV = ContentValues()
                    chapterCV.put(CHAPTER_DURATION, chapterDuration)
                    chapterCV.put(CHAPTER_NAME, chapterName)
                    chapterCV.put(CHAPTER_PATH, chapterPath)
                    chapterCV.put(BOOK_ID, bookId)

                    db.insert(TABLE_CHAPTERS, null, chapterCV)
                }

                for (j in 0..bookmarks.length() - 1) {
                    val bookmark = bookmarks.getJSONObject(j)
                    val bookmarkTime = bookmark.getInt("time")
                    val bookmarkPath = bookmark.getString("mediaPath")
                    val bookmarkTitle = bookmark.getString("title")

                    val bookmarkCV = ContentValues()
                    bookmarkCV.put(BOOKMARK_PATH, bookmarkPath)
                    bookmarkCV.put(BOOKMARK_TITLE, bookmarkTitle)
                    bookmarkCV.put(BOOKMARK_TIME, bookmarkTime)
                    bookmarkCV.put(BOOK_ID, bookId)

                    db.insert(TABLE_BOOKMARKS, null, bookmarkCV)
                }
            } catch (e: JSONException) {
                throw IllegalStateException(e)
            }
        }
    }


    /**
     * Queries through all books and removes the ones that were added empty by a bug.
     */
    private fun upgrade30() {
        // book keys
        val BOOK_ID = "bookId"
        val TABLE_BOOK = "tableBooks"
        val TABLE_CHAPTERS = "tableChapters"

        val bookCursor = db.query(TABLE_BOOK,
                arrayOf(BOOK_ID),
                null, null, null, null, null)

        bookCursor.use {
            while (bookCursor.moveToNext()) {
                val bookId = bookCursor.getLong(0)

                var chapterCount = 0
                val chapterCursor = db.query(TABLE_CHAPTERS,
                        null,
                        BOOK_ID + "=?",
                        arrayOf(bookId.toString()),
                        null, null, null)
                chapterCursor.use {
                    while (chapterCursor.moveToNext()) {
                        chapterCount++
                    }
                }
                if (chapterCount == 0) {
                    db.delete(TABLE_BOOK, BOOK_ID + "=?", arrayOf(bookId.toString()))
                }
            }
        }
    }

    /**
     * Corrects media paths that have been falsely set.
     */
    private fun upgrade31() {
        val BOOK_ID = "bookId"
        val TABLE_BOOK = "tableBooks"
        val TABLE_CHAPTERS = "tableChapters"
        val BOOK_CURRENT_MEDIA_PATH = "bookCurrentMediaPath"
        val CHAPTER_PATH = "chapterPath"

        val bookCursor = db.query(TABLE_BOOK,
                arrayOf(BOOK_ID, BOOK_CURRENT_MEDIA_PATH),
                null, null, null, null, null)
        bookCursor.use {
            while (bookCursor.moveToNext()) {
                val bookId = bookCursor.getLong(0)
                val bookmarkCurrentMediaPath = bookCursor.getString(1)

                val chapterCursor = db.query(TABLE_CHAPTERS,
                        arrayOf(CHAPTER_PATH),
                        BOOK_ID + "=?",
                        arrayOf(bookId.toString()),
                        null, null, null)
                val chapterPaths = ArrayList<String>(chapterCursor.count)
                chapterCursor.use {
                    while (chapterCursor.moveToNext()) {
                        val chapterPath = chapterCursor.getString(0)
                        chapterPaths.add(chapterPath)
                    }
                }

                if (chapterPaths.isEmpty()) {
                    db.delete(TABLE_BOOK, BOOK_ID + "=?", arrayOf(bookId.toString()))
                } else {
                    var mediaPathValid = false
                    for (s in chapterPaths) {
                        if (s == bookmarkCurrentMediaPath) {
                            mediaPathValid = true
                        }
                    }
                    if (!mediaPathValid) {
                        val cv = ContentValues()
                        cv.put(BOOK_CURRENT_MEDIA_PATH, chapterPaths[0])
                        db.update(TABLE_BOOK, cv, BOOK_ID + "=?", arrayOf(bookId.toString()))
                    }
                }
            }
        }
    }

    @Throws(InvalidPropertiesFormatException::class)
    fun upgrade(fromVersion: Int) {
        if (fromVersion <= 23) upgrade23()
        if (fromVersion <= 24) upgrade24()
        if (fromVersion <= 25) upgrade25()
        if (fromVersion <= 26) upgrade26()
        if (fromVersion <= 27) upgrade27()
        if (fromVersion <= 28) upgrade28()
        if (fromVersion <= 29) upgrade29()
        if (fromVersion <= 30) upgrade30()
        if (fromVersion <= 31) upgrade31()
    }
}