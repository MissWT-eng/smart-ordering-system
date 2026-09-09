package com.luwei.ordering.common.typehandler;

import com.luwei.ordering.common.enums.CodeEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CodeEnumTypeHandler<E extends Enum<E> & CodeEnum> extends BaseTypeHandler<E> {

    private final Class<E> type;

    // MyBatis 会通过反射调用这个 Class 构造器
    public CodeEnumTypeHandler(Class<E> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        this.type = type;
    }

    // 写：枚举 → 数据库（存 code）
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    // 读：数据库 → 枚举（按 code 找）
    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromCode(rs.getString(columnName));
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromCode(rs.getString(columnIndex));
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromCode(cs.getString(columnIndex));
    }

    private E fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知枚举值: " + code + "，类型: " + type.getSimpleName());
    }
}