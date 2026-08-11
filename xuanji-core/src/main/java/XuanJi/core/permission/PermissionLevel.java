package XuanJi.core.permission;

/**
 * 权限等级（升序）：
 * <pre>NONE(0) &lt; BLACKLIST(1) &lt; MEMBER(2) &lt; ADMIN(3) &lt; GROUP_OWNER(4) &lt; BOT_MASTER(5)</pre>
 *
 * <p>铁律：等级比较一律用 {@link #rank()} 做数值比较，不做字符串精确匹配。
 */
public enum PermissionLevel {

    NONE(0),
    BLACKLIST(1),
    MEMBER(2),
    ADMIN(3),
    GROUP_OWNER(4),
    BOT_MASTER(5);

    private final int rank;

    PermissionLevel(int rank) {
        this.rank = rank;
    }

    /** 数值等级，用于 {@code rank()} 比较。 */
    public int rank() {
        return rank;
    }

    /** 是否达到（含）指定等级。 */
    public boolean atLeast(PermissionLevel other) {
        return this.rank >= other.rank();
    }

    /** 是否低于指定等级。 */
    public boolean below(PermissionLevel other) {
        return this.rank < other.rank();
    }

    /** 由数值反查枚举，越界归 NONE。 */
    public static PermissionLevel of(int rank) {
        for (PermissionLevel lv : values()) {
            if (lv.rank == rank) return lv;
        }
        return NONE;
    }
}
