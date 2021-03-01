package model.rider

import com.jetbrains.rd.generator.nova.Ext
import com.jetbrains.rd.generator.nova.PredefinedType
import com.jetbrains.rd.generator.nova.PredefinedType.string
import com.jetbrains.rd.generator.nova.call
import com.jetbrains.rd.generator.nova.field
import com.jetbrains.rider.model.nova.ide.SolutionModel

@Suppress("unused")
object RiderProjectOutputModel : Ext(SolutionModel.Solution) {

    private val RdGetProjectOutputArgs = structdef {
        field("projectFilePath", string)
    }

    private val rdTargetFrameworkIdMock = structdef { // TODO[F]: Use the original type when available
        field("shortName", string)
        field("presentableName", string)
        field("isNetCoreApp", PredefinedType.bool)
        field("isNetFramework", PredefinedType.bool)
    }

    private val RdProjectOutput = structdef {
        field("tfm", rdTargetFrameworkIdMock)
        field("outputPath", string)
    }

    init {
        call("getProjectOutput", RdGetProjectOutputArgs, RdProjectOutput)
    }
}
