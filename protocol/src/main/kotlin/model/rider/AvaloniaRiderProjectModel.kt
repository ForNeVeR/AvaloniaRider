package model.rider

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.string
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel
import com.jetbrains.rider.model.nova.ide.rider.RunnableProjectsModel.rdTargetFrameworkId

@Suppress("unused")
object AvaloniaRiderProjectModel : Ext(SolutionModel.Solution) {

    private val RdGetProjectOutputArgs = structdef {
        field("projectFilePath", string)
    }

    private val RdProjectOutput = structdef {
        field("tfm", rdTargetFrameworkId)
        field("outputPath", string)
    }

    private val RdGetReferencingProjectsRequest = structdef {
        field("targetProjectFilePath", string)
        field("potentiallyReferencingProjects", immutableList(string))
    }

    init {
        setting(Kotlin11Generator.Namespace, "me.fornever.avaloniarider.model")
        setting(CSharp50Generator.Namespace, "AvaloniaRider.Model")

        call("getProjectOutput", RdGetProjectOutputArgs, RdProjectOutput)
        call("getReferencingProjects", RdGetReferencingProjectsRequest, immutableList(string))
            .doc("Checks the potentially referencing projects, and returns only the ones actually referencing the target")
    }
}
