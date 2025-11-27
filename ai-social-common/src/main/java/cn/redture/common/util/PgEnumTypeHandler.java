// cn.redture.common.typehandler.PgEnumTypeHandler.java
package cn.redture.common.util;

import cn.redture.common.annotation.PgEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PgEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private final Class<E> type;
    private final String pgEnumTypeName;

    public PgEnumTypeHandler(Class<E> type) {
        if (!type.isAnnotationPresent(PgEnum.class)) {
            throw new IllegalArgumentException("枚举 " + type.getSimpleName() + " 必须使用 @PgEnum 注解");
        }
        this.type = type;
        this.pgEnumTypeName = type.getAnnotation(PgEnum.class).value();
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType(pgEnumTypeName);
        pgObject.setValue(parameter.name());
        ps.setObject(i, pgObject);
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : Enum.valueOf(type, value);
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : Enum.valueOf(type, value);
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : Enum.valueOf(type, value);
    }
}