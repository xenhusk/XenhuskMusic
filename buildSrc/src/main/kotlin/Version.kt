/*
 * Copyright (c) 2025 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

sealed class Version(
    private val versionOffset: Long,
    private val versionMajor: Int,
    private val versionMinor: Int,
    private val versionPatch: Int,
    private val versionBuild: Int = 0,
    private val versionType: String = ""
) {
    init {
        require(versionMajor >= 0 && versionMinor >= 0 && versionPatch >= 0 && versionBuild >= 0) {
            "Version numbers must be non-negative"
        }
    }

    class Alpha(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(ALPHA, versionMajor, versionMinor, versionPatch, versionBuild, "alpha")

    class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(BETA, versionMajor, versionMinor, versionPatch, versionBuild, "beta")

    class RC(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(RELEASE_CANDIDATE, versionMajor, versionMinor, versionPatch, versionBuild, "rc")

    class Stable(versionMajor: Int, versionMinor: Int, versionPatch: Int) :
        Version(STABLE, versionMajor, versionMinor, versionPatch)

    val name: String
        get() {
            val versionName = "${versionMajor}.${versionMinor}.${versionPatch}"
            return if (versionType.isNotEmpty()) "$versionName-${versionType}.$versionBuild" else versionName
        }

    val code: Int
        get() {
            val versionCode = versionMajor * MAJOR +
                    versionMinor * MINOR +
                    versionPatch * PATCH +
                    versionOffset * VARIANT +
                    versionBuild
            require(versionCode <= Int.MAX_VALUE) {
                "Version code exceeds Int.MAX_VALUE"
            }
            return versionCode.toInt()
        }
}

private const val MAJOR = 1_000_000L
private const val MINOR = 100_000L
private const val PATCH = 10_000L
private const val VARIANT = 100L

private const val ALPHA = VARIANT * 0
private const val BETA = VARIANT * 1
private const val RELEASE_CANDIDATE = VARIANT * 2
private const val STABLE = VARIANT * 3

val currentVersion: Version = Version.Alpha(
    versionMajor = 1,
    versionMinor = 0,
    versionPatch = 0,
    versionBuild = 7
)