package database.models

import com.google.gson.JsonArray
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.misc.TransactionManager
import com.j256.ormlite.table.DatabaseTable
import database.*
import gson
import org.jetbrains.annotations.NotNull

@DatabaseTable(tableName = "thingy")
class Thingy {
    @NotNull
    @DatabaseField()
    var version: Int = 0

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    companion object {
        fun getCurrent(): Thingy {
            var result = thingyDao.queryBuilder().selectColumns().limit(1).queryForFirst()
            if (result == null) {
                result = Thingy()
                thingyDao.create(result)
            }
            return result
        }
    }

    fun runMigration() {
        if (version == 0) {
            TransactionManager.callInTransaction(connectionSource) {
                // Update all chapters/entries
                val entries = chapterEntryDao.queryBuilder().selectColumns().query()
                for (entry in entries) {
                    val parameters = gson.fromJson(entry.parameters, JsonArray::class.java)
                    /*
                    [{
	"artID": "Thingy-LtQIedV3gVjSNd2XOHS6jFD8xKosjW0F",
	"seed": 1,
	"stableDiffusionParameters": {
		"prompt": "Poop angry",
		"ratio": {
			"w": 1,
			"h": 1
		},
		"steps": 50,
		"guidanceScale": 7.5
	}
}, {
	"artID": "Thingy-AUFFr0cmEMf0QbnBU6BK7fKMGym2HOYv",
	"seed": 2,
	"stableDiffusionParameters": {
		"prompt": "Poop angry",
		"ratio": {
			"w": 1,
			"h": 1
		},
		"steps": 50,
		"guidanceScale": 7.5
	}
}, {
	"artID": "Thingy-zmV5nGNz9tM7bpp7wd9ZGRwhwSrY9sFB",
	"seed": 3,
	"stableDiffusionParameters": {
		"prompt": "Poop angry",
		"ratio": {
			"w": 1,
			"h": 1
		},
		"steps": 50,
		"guidanceScale": 7.5
	}
}, {
	"artID": "Thingy-hjlFMQdGd4chKf4jrz3M5QBCh9QStfq5",
	"seed": 4,
	"stableDiffusionParameters": {
		"prompt": "Poop angry",
		"ratio": {
			"w": 1,
			"h": 1
		},
		"steps": 50,
		"guidanceScale": 7.5
	}
}]

[{
	"prompt": "Grilled dragon, served on a plate, realistic food photo, kodak ektar, Michelin Star dish",
	"ar": "6:4",
	"size": 768,
	"seed": 1907389727,
	"_hf_auth_token": "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",
	"guidance_scale": 9.0,
	"steps": 25
}, {
	"prompt": "Grilled dragon, served on a plate, realistic food photo, kodak ektar, Michelin Star dish",
	"ar": "6:4",
	"size": 768,
	"seed": 910906283,
	"_hf_auth_token": "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",
	"guidance_scale": 9.0,
	"steps": 25
}, {
	"prompt": "Grilled dragon, served on a plate, realistic food photo, kodak ektar, Michelin Star dish",
	"ar": "6:4",
	"size": 768,
	"seed": 425459156,
	"_hf_auth_token": "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",
	"guidance_scale": 9.0,
	"steps": 25
}, {
	"prompt": "Grilled dragon, served on a plate, realistic food photo, kodak ektar, Michelin Star dish",
	"ar": "6:4",
	"size": 768,
	"seed": 851511266,
	"_hf_auth_token": "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",
	"guidance_scale": 9.0,
	"steps": 25
}]
                     */
                    for (obj in parameters) {
                        println(obj.toString())
                        val obj = obj.asJsonObject
                        if (obj.has("stableDiffusionParameters")) {
                            for ((key, value) in obj.getAsJsonObject("stableDiffusionParameters").asMap()) {
                                obj.add(key, value)
                            }
                        }
                        obj.remove("stableDiffusionParameters")
                        for ((key, value) in obj.deepCopy().asMap()) {
                            if (value.toString().length > 2048) {
                                obj.remove(key)
                            }
                        }
                    }
                    val updateBuilder = chapterEntryDao.updateBuilder()
                    updateBuilder.where().eq("id", entry.id)
                    updateBuilder.updateColumnValue("parameters", parameters.toString().replace("'", "''"))
                    println(updateBuilder.prepareStatementString())
                    updateBuilder.update()
                }
                val updateBuilder = thingyDao.updateBuilder()
                updateBuilder.updateColumnValue("version", 1)
                updateBuilder.update()
            }
        }
    }
}