package ro.medatlas.study

/**
 * UI capabilities are determined by available screen width, not by device
 * manufacturer. Foldables and desktop Android windows can switch profiles at
 * runtime without separate packages or separate user data.
 *
 * The phone workspace intentionally exposes just reader and notes; advanced
 * study and classroom screens remain available in the tablet workspace.
 */
internal enum class WorkspaceProfile { PHONE, TABLET }

internal object DeviceExperience {
    fun profile(smallestWidthDp: Int): WorkspaceProfile =
        if (smallestWidthDp in 1..599) WorkspaceProfile.PHONE else WorkspaceProfile.TABLET

    fun allowedTabs(profile: WorkspaceProfile): Set<Int> =
        if (profile == WorkspaceProfile.PHONE) setOf(0, 1)
        else setOf(0, 1, 2, 3, 4)

    fun allowsDocumentEditing(profile: WorkspaceProfile) =
        profile != WorkspaceProfile.PHONE
}
