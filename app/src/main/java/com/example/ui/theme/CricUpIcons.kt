package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lightweight bundled Material 24dp vector icons replacing `material-icons-extended`
 * (~2,100 unused icons), shrinking APK size by ~10+ MB and speeding up cold startup.
 */
object CricUpIcons {
    private val solidFill = SolidColor(Color.Black)

    private fun buildIcon(
        name: String,
        autoMirror: Boolean = false,
        vararg paths: String
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror
        )
        for (pathData in paths) {
            val nodes = PathParser().parsePathString(pathData).toNodes()
            builder.addPath(
                pathData = nodes,
                pathFillType = PathFillType.NonZero,
                fill = solidFill,
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1.0f
            )
        }
        return builder.build()
    }

    val CloudDone: ImageVector by lazy {
        buildIcon(
            "CloudDone",
            false,
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM10,17l-3.5,-3.5 1.41,-1.41L10,14.17 15.18,9l1.41,1.41L10,17z"
        )
    }

    val ContentCopy: ImageVector by lazy {
        buildIcon(
            "ContentCopy",
            false,
            "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z"
        )
    }

    val DarkMode: ImageVector by lazy {
        buildIcon(
            "DarkMode",
            false,
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36c-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3L12,3z"
        )
    }

    val ExpandLess: ImageVector by lazy {
        buildIcon(
            "ExpandLess",
            false,
            "M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z"
        )
    }

    val ExpandMore: ImageVector by lazy {
        buildIcon(
            "ExpandMore",
            false,
            "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z"
        )
    }

    val Group: ImageVector by lazy {
        buildIcon(
            "Group",
            false,
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z"
        )
    }

    val GroupAdd: ImageVector by lazy {
        buildIcon(
            "GroupAdd",
            false,
            "M22,9L22,7h-2v2h-2v2h2v2h2v-2h2L24,9zM8,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM8,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4zM12.51,4.05C13.43,5.11 14,6.49 14,8s-0.57,2.89 -1.49,3.95C14.47,11.7 16,10.04 16,8s-1.53,-3.7 -3.49,-3.95zM16.53,13.83C17.42,14.66 18,15.7 18,17v3h2v-3c0,-1.45 -1.59,-2.51 -3.47,-3.17z"
        )
    }

    val History: ImageVector by lazy {
        buildIcon(
            "History",
            false,
            "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z"
        )
    }

    val Image: ImageVector by lazy {
        buildIcon(
            "Image",
            false,
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"
        )
    }

    val Leaderboard: ImageVector by lazy {
        buildIcon(
            "Leaderboard",
            false,
            "M7.5,21H2V9h5.5V21zM14.75,3h-5.5v18h5.5V3zM22,11h-5.5v10H22V11z"
        )
    }

    val LightMode: ImageVector by lazy {
        buildIcon(
            "LightMode",
            false,
            "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5S14.76,7 12,7L12,7zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1S1.45,13 2,13zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1S19.45,13 20,13zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1S11,1.45 11,2zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1C11.45,19 11,19.45 11,20zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0c0.39,-0.39 0.39,-1.03 0,-1.41L18.36,16.95zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L19.42,5.99zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L7.05,18.36z"
        )
    }

    val Link: ImageVector by lazy {
        buildIcon(
            "Link",
            false,
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"
        )
    }

    val Login: ImageVector by lazy {
        buildIcon(
            "Login",
            true,
            "M11,7L9.6,8.4l2.6,2.6H2v2h10.2l-2.6,2.6L11,17l5,-5L11,7zM20,19h-8v2h8c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2h-8v2h8V19z"
        )
    }

    val Logout: ImageVector by lazy {
        buildIcon(
            "Logout",
            true,
            "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z"
        )
    }

    val Mic: ImageVector by lazy {
        buildIcon(
            "Mic",
            false,
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"
        )
    }

    val MoreHoriz: ImageVector by lazy {
        buildIcon(
            "MoreHoriz",
            false,
            "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
        )
    }

    val People: ImageVector by lazy { Group }

    val PersonAdd: ImageVector by lazy {
        buildIcon(
            "PersonAdd",
            false,
            "M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM6,10L6,7L4,7v3L1,10v2h3v3h2v-3h3v-2L6,10zM15,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
        )
    }

    val Sensors: ImageVector by lazy {
        buildIcon(
            "Sensors",
            false,
            "M7.76,16.24C6.67,15.16 6,13.66 6,12s0.67,-3.16 1.76,-4.24l1.42,1.42C8.45,9.9 8,10.9 8,12c0,1.1 0.45,2.1 1.17,2.83L7.76,16.24zM16.24,16.24C17.33,15.16 18,13.66 18,12s-0.67,-3.16 -1.76,-4.24l-1.42,1.42C15.55,9.9 16,10.9 16,12s-0.45,2.1 -1.17,2.83L16.24,16.24zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2S13.1,10 12,10zM20,12c0,2.21 -0.9,4.21 -2.35,5.65l1.42,1.42C20.88,17.26 22,14.76 22,12s-1.12,-5.26 -2.93,-7.07l-1.42,1.42C19.1,7.79 20,9.79 20,12zM6.35,6.35L4.93,4.93C3.12,6.74 2,9.24 2,12s1.12,5.26 2.93,7.07l1.42,-1.42C4.9,16.21 4,14.21 4,12S4.9,7.79 6.35,6.35z"
        )
    }

    val SportsCricket: ImageVector by lazy {
        buildIcon(
            "SportsCricket",
            false,
            "M15.04,12.79l-8.5,-8.5C6.35,4.1 6.09,4 5.83,4S5.32,4.1 5.13,4.29L2.29,7.13c-0.39,0.39 -0.39,1.03 0,1.42l8.5,8.5c0.2,0.2 0.45,0.29 0.71,0.29c0.26,0 0.51,-0.1 0.71,-0.29l2.83,-2.83C15.43,13.82 15.43,13.18 15.04,12.79z",
            "M14.34,17.76l3.53,3.53c0.39,0.39 1.03,0.39 1.42,0c0.39,-0.39 0.39,-1.03 0,-1.42l-3.53,-3.53L14.34,17.76z",
            "M18.5,2C16.57,2 15,3.57 15,5.5C15,7.43 16.57,9 18.5,9C20.43,9 22,7.43 22,5.5C22,3.57 20.43,2 18.5,2z"
        )
    }

    val SwapHoriz: ImageVector by lazy {
        buildIcon(
            "SwapHoriz",
            false,
            "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z"
        )
    }

    val Undo: ImageVector by lazy {
        buildIcon(
            "Undo",
            false,
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z"
        )
    }

    val VerifiedUser: ImageVector by lazy {
        buildIcon(
            "VerifiedUser",
            false,
            "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4zM10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9l-8,8z"
        )
    }

    val Videocam: ImageVector by lazy {
        buildIcon(
            "Videocam",
            false,
            "M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z"
        )
    }

    val Visibility: ImageVector by lazy {
        buildIcon(
            "Visibility",
            false,
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
        )
    }

    val WorkspacePremium: ImageVector by lazy {
        buildIcon(
            "WorkspacePremium",
            false,
            "M9.68,13.69L12,11.93l2.31,1.76l-0.88,-2.85L15.75,9h-2.84L12,6.19L11.09,9H8.25l2.31,1.84L9.68,13.69zM20,10c0,-4.42 -3.58,-8 -8,-8s-8,3.58 -8,8c0,2.03 0.76,3.87 2,5.28V23l6,-2l6,2v-7.72C19.24,13.87 20,12.03 20,10zM12,4c3.31,0 6,2.69 6,6s-2.69,6 -6,6s-6,-2.69 -6,-6S8.69,4 12,4zM12,19l-4,1.02v-3.1C9.18,17.6 10.54,18 12,18s2.82,-0.4 4,-1.08v3.1L12,19z"
        )
    }

    val ChatBubbleOutline: ImageVector by lazy {
        buildIcon(
            "ChatBubbleOutline",
            false,
            "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H6l-2,2V4h16v12z"
        )
    }

    val OpenInNew: ImageVector by lazy {
        buildIcon(
            "OpenInNew",
            true,
            "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z"
        )
    }

    val VolumeUp: ImageVector by lazy {
        buildIcon(
            "VolumeUp",
            false,
            "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
        )
    }

    val VolumeOff: ImageVector by lazy {
        buildIcon(
            "VolumeOff",
            false,
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z"
        )
    }

    val Fullscreen: ImageVector by lazy {
        buildIcon(
            "Fullscreen",
            false,
            "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z"
        )
    }
}
