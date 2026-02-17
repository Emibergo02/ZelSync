package dev.unnm3d.zelsync.data;

import dev.unnm3d.zelsync.ZelSync;
import dev.unnm3d.zelsync.core.NewTrade;
import dev.unnm3d.zeltrade.api.data.IStorageData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public interface Database extends IStorageData<NewTrade> {

    @NotNull
    default String[] getSchemaStatements(@NotNull String schemaFileName) throws IOException {
        return new String(Objects.requireNonNull(ZelSync.getInstance().getResource(schemaFileName))
          .readAllBytes(), StandardCharsets.UTF_8).split(";");
    }

    void connect();

    Connection getConnection() throws SQLException;

}
