package script

import database.thingyExtensionDao
import net.dv8tion.jda.api.JDA
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

fun runScripts(jda: JDA) {
    val scriptsToRun = thingyExtensionDao.queryBuilder().selectColumns().where().eq("enabled", true).query()
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<ThingyExtensionScript>()
    val evaluationConfiguration = ThingyExtensionScriptEvaluationConfiguration(jda)

    for(script in scriptsToRun) {
        val result = BasicJvmScriptingHost().eval(script.code.toScriptSource("main.thingyextension.kts"), compilationConfiguration, evaluationConfiguration)
        result.reports.forEach {
            if (it.severity > ScriptDiagnostic.Severity.DEBUG) {
                println(" : ${it.message}" + if (it.exception == null) "" else ": ${it.exception}")
            }
        }
    }
}