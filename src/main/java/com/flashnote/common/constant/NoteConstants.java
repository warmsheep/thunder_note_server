package com.flashnote.common.constant;

/**
 * 闪记模块常量集中管理。
 */
public final class NoteConstants {

    private NoteConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 收集箱对应的闪记 ID（魔法数字）。
     * 在数据库中，收集箱不是一条真正的闪记记录，
     * 其 flash_note_id 字段值为 -1L 作为标识。
     */
    public static final long COLLECTION_BOX_NOTE_ID = -1L;

    /**
     * 收集箱默认标题。
     */
    public static final String COLLECTION_BOX_TITLE = "收集箱";

    /**
     * 收集箱默认图标。
     */
    public static final String COLLECTION_BOX_ICON = "📥";
}
