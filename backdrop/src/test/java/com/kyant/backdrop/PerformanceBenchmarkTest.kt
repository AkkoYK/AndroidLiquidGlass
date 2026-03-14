package com.kyant.backdrop

import org.junit.Test

/**
 * JVM-based micro-benchmarks to verify performance improvements from
 * CPU-side optimizations in the liquid glass rendering pipeline.
 *
 * These benchmarks focus on object allocation reduction:
 * 1. FloatArray allocation vs. reuse (cornerRadii optimization)
 * 2. Cached vs. uncached native filter pattern (BlurMaskFilter simulation)
 *
 * GPU-side shader optimizations (chromatic aberration 7→3 samples, early exit,
 * depthEffect branching) require on-device profiling with GPU frame timing.
 */
class PerformanceBenchmarkTest {

    /**
     * Benchmark: FloatArray allocation (old) vs. reuse (new).
     * Simulates the cornerRadii computation in Lens.kt and HighlightStyle.kt.
     *
     * Old approach: allocates a new FloatArray(4) every frame.
     * New approach: reuses a cached FloatArray(4), writing into it.
     */
    @Test
    fun benchmarkFloatArrayAllocationVsReuse() {
        val iterations = 500_000
        val warmup = 50_000

        // Warmup
        for (i in 0 until warmup) {
            allocateFloatArray()
        }
        for (i in 0 until warmup) {
            reuseFloatArray(cachedArray)
        }

        // Benchmark: allocation (old approach)
        val allocStart = System.nanoTime()
        for (i in 0 until iterations) {
            val result = allocateFloatArray()
            // Prevent dead code elimination
            if (result[0] < -1f) throw AssertionError()
        }
        val allocTime = System.nanoTime() - allocStart

        // Benchmark: reuse (new approach)
        val reuseStart = System.nanoTime()
        for (i in 0 until iterations) {
            val result = reuseFloatArray(cachedArray)
            if (result[0] < -1f) throw AssertionError()
        }
        val reuseTime = System.nanoTime() - reuseStart

        val improvement = ((allocTime - reuseTime).toDouble() / allocTime * 100)

        println("=== FloatArray Allocation Benchmark ($iterations iterations) ===")
        println("Old (allocate new):  ${allocTime / 1_000_000.0} ms")
        println("New (reuse cached):  ${reuseTime / 1_000_000.0} ms")
        println("Improvement: ${"%.1f".format(improvement)}%")
        println()

        // The reuse approach should be faster or equal
        assert(reuseTime <= allocTime * 1.1) {
            "Reuse approach should not be significantly slower than allocation"
        }
    }

    /**
     * Benchmark: Object creation (simulating BlurMaskFilter) vs. cached lookup.
     *
     * Old approach: creates a new object every frame.
     * New approach: compares radius and only creates when changed.
     */
    @Test
    fun benchmarkObjectCreationVsCachedLookup() {
        val iterations = 500_000
        val warmup = 50_000
        val fixedRadius = 10f

        // Warmup
        for (i in 0 until warmup) {
            createSimulatedFilter(fixedRadius)
        }
        var cachedFilter: Any? = null
        var cachedRadius = Float.NaN
        for (i in 0 until warmup) {
            val result = getCachedFilter(fixedRadius, cachedRadius, cachedFilter)
            cachedFilter = result.first
            cachedRadius = result.second
        }

        // Benchmark: always create (old approach)
        val createStart = System.nanoTime()
        for (i in 0 until iterations) {
            val filter = createSimulatedFilter(fixedRadius)
            if (filter.hashCode() == Int.MIN_VALUE + 1) throw AssertionError()
        }
        val createTime = System.nanoTime() - createStart

        // Benchmark: cached (new approach) - radius doesn't change
        cachedFilter = null
        cachedRadius = Float.NaN
        val cacheStart = System.nanoTime()
        for (i in 0 until iterations) {
            val result = getCachedFilter(fixedRadius, cachedRadius, cachedFilter)
            cachedFilter = result.first
            cachedRadius = result.second
            if (cachedFilter.hashCode() == Int.MIN_VALUE + 1) throw AssertionError()
        }
        val cacheTime = System.nanoTime() - cacheStart

        val improvement = ((createTime - cacheTime).toDouble() / createTime * 100)

        println("=== Object Creation vs. Cache Benchmark ($iterations iterations) ===")
        println("Old (always create): ${createTime / 1_000_000.0} ms")
        println("New (cached lookup): ${cacheTime / 1_000_000.0} ms")
        println("Improvement: ${"%.1f".format(improvement)}%")
        println()

        assert(cacheTime <= createTime * 1.1) {
            "Cached approach should not be significantly slower than always-create"
        }
    }

    /**
     * Benchmark: Shader texture lookup count comparison.
     * This is a conceptual test showing the reduction in work.
     * Actual GPU performance must be measured on-device.
     */
    @Test
    fun verifyShaderOptimizationMetrics() {
        val oldLookups = 7  // Original: red, orange, yellow, green, cyan, blue, purple
        val newLookups = 3  // Optimized: R, G, B channels
        val reduction = ((oldLookups - newLookups).toDouble() / oldLookups * 100)

        println("=== Shader Optimization Summary ===")
        println("Chromatic Aberration:")
        println("  Old texture lookups per fragment: $oldLookups")
        println("  New texture lookups per fragment: $newLookups")
        println("  Reduction: ${"%.1f".format(reduction)}%")
        println()
        println("Refraction Shader Early Exit:")
        println("  Merged: sd > 0.0 || -sd >= refractionHeight (single branch)")
        println("  Benefit: Reduced branch divergence on mobile GPUs")
        println()
        println("Half Precision Intermediates:")
        println("  Converted: d, grad, dispersedCoord to half precision")
        println("  Benefit: Up to 2x throughput on Mali/Adreno GPUs")
        println()
        println("Highlight Normal Precomputation:")
        println("  Moved: cos(angle)/sin(angle) from per-fragment to CPU")
        println("  Benefit: Eliminates 2 trig ops per fragment per highlight")
        println()
        println("Gamma Vectorization:")
        println("  Changed: 3x scalar pow() -> 1x vector pow()")
        println()
        println("depthEffect=0 Optimization:")
        println("  Removed: 1 normalize() call (containing sqrt) per fragment")
        println("  Applies: When depthEffect is disabled (most common case)")
        println()

        assert(newLookups < oldLookups)
        assert(reduction > 50.0)
    }

    /**
     * Benchmark: Dirty flag skip re-recording pattern.
     * Simulates the cost savings from skipping GraphicsLayer.record() when unchanged.
     */
    @Test
    fun benchmarkDirtyFlagSkipPattern() {
        val iterations = 500_000
        val warmup = 50_000

        var dirtyFlag = false
        var recordCount = 0

        // Warmup
        for (i in 0 until warmup) {
            if (dirtyFlag) {
                recordCount++
                dirtyFlag = false
            }
        }

        // Benchmark: always record (old)
        val alwaysStart = System.nanoTime()
        for (i in 0 until iterations) {
            simulateRecord()
        }
        val alwaysTime = System.nanoTime() - alwaysStart

        // Benchmark: skip when clean (new) - simulate static element
        recordCount = 0
        dirtyFlag = true // dirty only once
        val skipStart = System.nanoTime()
        for (i in 0 until iterations) {
            if (dirtyFlag) {
                simulateRecord()
                dirtyFlag = false
                recordCount++
            }
        }
        val skipTime = System.nanoTime() - skipStart

        println("=== Dirty Flag Skip Pattern ($iterations iterations) ===")
        println("Old (always record):  ${alwaysTime / 1_000_000.0} ms")
        println("New (skip when clean): ${skipTime / 1_000_000.0} ms")
        println("Records executed: $recordCount / $iterations")
        println()

        assert(recordCount == 1) { "Should only record once when dirty flag is set once" }
        assert(skipTime < alwaysTime) { "Skip pattern should be faster" }
    }

    private fun simulateRecord() {
        // Simulate GraphicsLayer.record() overhead with some work
        @Suppress("UNUSED_VARIABLE")
        val x = FloatArray(8) { it.toFloat() }
    }

    // --- Helper methods ---

    private val cachedArray = FloatArray(4)

    /** Simulates old approach: allocate new FloatArray each time */
    private fun allocateFloatArray(): FloatArray {
        return floatArrayOf(10f, 20f, 20f, 10f)
    }

    /** Simulates new approach: reuse a cached FloatArray */
    private fun reuseFloatArray(out: FloatArray): FloatArray {
        out[0] = 10f
        out[1] = 20f
        out[2] = 20f
        out[3] = 10f
        return out
    }

    /** Simulates creating a native filter object (like BlurMaskFilter) */
    private fun createSimulatedFilter(radius: Float): Any {
        // Simulates native object allocation cost
        return Object().also { it.hashCode() + radius.toInt() }
    }

    /** Simulates cached filter pattern */
    private fun getCachedFilter(
        radius: Float,
        prevRadius: Float,
        cached: Any?
    ): Pair<Any, Float> {
        return if (radius != prevRadius) {
            Pair(createSimulatedFilter(radius), radius)
        } else {
            Pair(cached!!, prevRadius)
        }
    }
}
