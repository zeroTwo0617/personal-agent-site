package me.zhengziheng.agent.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * float[] <-> pgvector 类型处理器。
 * 不依赖 pgvector Java 客户端，直接把 float[] 格式化为向量字面量字符串 "[0.1,0.2,...]"，
 * 以 setString 写入；SQL 侧通过 `?::vector` 强转为 vector 类型。
 * 这样编译期完全不引用 pgvector 库 / pgjdbc 内部类（如 PGBinaryObject），对 IDE 与连接池都最稳。
 */
public class VectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, toPgVectorString(parameter));
    }

    private static String toPgVectorString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < vec.length; k++) {
            if (k > 0) {
                sb.append(',');
            }
            sb.append(vec[k]);
        }
        return sb.append(']').toString();
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) {
        return null;
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) {
        return null;
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) {
        return null;
    }
}
