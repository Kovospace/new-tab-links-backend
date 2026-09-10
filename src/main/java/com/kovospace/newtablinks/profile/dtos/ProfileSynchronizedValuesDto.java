package com.kovospace.newtablinks.profile.dtos;

/**
 * The fields of a profile that a pushed synchronization operation replaces wholesale.
 *
 * <p>Distinct from {@link ProfileSaveRequestDto} because synchronization sets the display
 * position as well. The interactive endpoints deliberately do not: reordering there is a
 * separate concern that has no operation yet, whereas a pushed change is exactly the "dedicated
 * move operation" the rest of this codebase has been waiting for.</p>
 *
 * @param name              name shown on the profile switcher
 * @param enableDragAndDrop whether the profile lets its links and groups be rearranged by dragging
 * @param hideTips          whether the profile hides the tips shown on the new tab page background
 * @param position          zero based position among the owner's profiles
 * @since 0.0.6
 */
public record ProfileSynchronizedValuesDto(
        String name,
        boolean enableDragAndDrop,
        boolean hideTips,
        int position) {
}
