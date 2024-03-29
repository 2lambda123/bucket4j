package io.github.bucket4j.distributed.proxy.optimization.batch.mock

import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.LoggingLevel
import org.jetbrains.kotlinx.lincheck.Options
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.LongGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState
import org.jetbrains.kotlinx.lincheck.verifier.linearizability.LinearizabilityVerifier
import org.junit.jupiter.api.Test

@StressCTest(verifier = LinearizabilityVerifier::class)
@Param(name = "amount", gen = LongGen::class, conf = "1:20")
class BatchingExecutorLincheckTest  : VerifierState() {

    private val mockExecutor = MockBatchExecutor()

    @Operation
    fun testBatching(@Param(name = "amount") amount: Long): Long {
        val cmd = SingleMockCommand(amount)
        return mockExecutor.syncBatchHelper.execute(cmd)
    }

    @Test
    fun runTest() {
        val opts: Options<*, *> = StressOptions()
                .iterations(10)
                .threads(3)
                .minimizeFailedScenario(true)
                .logLevel(LoggingLevel.INFO)
        LinChecker.check(BatchingExecutorLincheckTest::class.java, opts)
    }

    override fun extractState(): Any {
        return mockExecutor.state.sum
    }

}