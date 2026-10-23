package timetablescheduler.domain.scheduling.pareto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeightVectorsTest {
    @Test
    fun `first vector weights all objectives equally`() {
        assertEquals(listOf(0.25, 0.25, 0.25, 0.25), WeightVectors.forObjectives(4).first())
    }

    @Test
    fun `then each objective is emphasized once in turn`() {
        val vectors = WeightVectors.forObjectives(3)

        (0 until 3).forEach { objective ->
            val emphasized = vectors[1 + objective]
            assertEquals(objective, emphasized.indices.maxBy { emphasized[it] })
        }
    }

    @Test
    fun `there are 2 + objectives + 2 vectors, each summing to 1`() {
        (1..7).forEach { objectives ->
            val vectors = WeightVectors.forObjectives(objectives)

            assertEquals(2 + objectives + 2, vectors.size)
            vectors.forEach { vector ->
                assertEquals(objectives, vector.size)
                assertEquals(1.0, vector.sum(), 1e-9)
                assertTrue(vector.all { it >= 0.0 })
            }
        }
    }

    @Test
    fun `spread vectors differ from the equal and the emphasized ones`() {
        val vectors = WeightVectors.forObjectives(2)

        assertEquals(vectors.size, vectors.toSet().size)
    }
}
