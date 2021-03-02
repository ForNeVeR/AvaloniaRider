package model.rider

import com.jetbrains.rd.generator.nova.Ext
import com.jetbrains.rd.generator.nova.PredefinedType.string
import com.jetbrains.rd.generator.nova.call
import com.jetbrains.rd.generator.nova.field
import com.jetbrains.rider.model.nova.ide.SolutionModel
import com.jetbrains.rider.model.nova.ide.rider.RunnableProjectsModel.rdTargetFrameworkId

@Suppress("unused")
object RiderProjectOutputModel : Ext(SolutionModel.Solution) {

    private val RdGetProjectOutputArgs = structdef {
        field("projectFilePath", string)
    }

    private val RdProjectOutput = structdef {
        field("tfm", rdTargetFrameworkId)
        field("outputPath", string)
    }

    init {
        call("getProjectOutput", RdGetProjectOutputArgs, RdProjectOutput)
    }
}
