package vip.mate.tool.builtin;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Mammoth HTML table grid reconstruction in {@link DocumentExtractTool}.
 *
 * <p>Mammoth emits {@code colspan} for horizontal merges and {@code rowspan} for vertical
 * merges, but does not emit placeholder {@code td} elements for cells covered by a vertical
 * merge. The reconstructed grid must therefore be derived from all rows, not from each row's
 * {@code td} count.
 */
class DocumentExtractToolDocxTablesTest {

    @Test
    @DisplayName("mixed rowspan/colspan: grid keeps merge coverage as null and cells carry spans")
    void parseHtmlTables_mixedMerges() {
        String html = """
                <table>
                  <tr><th rowspan="2">部门</th><th colspan="2">一季度</th></tr>
                  <tr><td>1月</td><td>2月</td></tr>
                  <tr><td>研发</td><td>10</td><td>20</td></tr>
                </table>
                """;

        JSONArray tables = DocumentExtractTool.parseHtmlTables(html);

        assertThat(tables).hasSize(1);
        JSONObject table = tables.getJSONObject(0);
        assertThat(table.getInt("rowCount")).isEqualTo(3);
        assertThat(table.getInt("columnCount")).isEqualTo(3);

        JSONArray grid = table.getJSONArray("grid");
        assertThat(grid.getJSONArray(0).get(0)).isEqualTo("部门");
        assertThat(grid.getJSONArray(0).get(1)).isEqualTo("一季度");
        // covered by the colspan=2 anchor
        assertThat(grid.getJSONArray(0).get(2)).isNull();
        // covered by the rowspan=2 anchor
        assertThat(grid.getJSONArray(1).get(0)).isNull();
        assertThat(grid.getJSONArray(1).get(1)).isEqualTo("1月");
        assertThat(grid.getJSONArray(2).get(0)).isEqualTo("研发");

        JSONArray cells = table.getJSONArray("cells");
        assertThat(cells).hasSize(7);
        JSONObject first = cells.getJSONObject(0);
        assertThat(first.getInt("row")).isZero();
        assertThat(first.getInt("col")).isZero();
        assertThat(first.getInt("rowSpan")).isEqualTo(2);
        assertThat(first.getInt("colSpan")).isEqualTo(1);
        assertThat(first.getBool("header")).isTrue();
        assertThat(first.getBool("merged")).isTrue();

        assertThat(table.getJSONArray("merges")).hasSize(2);
    }

    @Test
    @DisplayName("nested tables are parsed independently and do not steal outer rows")
    void parseHtmlTables_nestedTable() {
        String html = "<table id='outer'><tr><td>A</td><td><table><tr><td>inner</td></tr></table></td></tr></table>";

        JSONArray tables = DocumentExtractTool.parseHtmlTables(html);

        assertThat(tables).hasSize(2);
        JSONObject outer = tables.getJSONObject(0);
        assertThat(outer.getInt("rowCount")).isEqualTo(1);
        assertThat(outer.getInt("columnCount")).isEqualTo(2);
        assertThat(outer.getJSONArray("cells")).hasSize(2);
    }

    @Test
    @DisplayName("empty HTML yields an empty table array")
    void parseHtmlTables_empty() {
        assertThat(DocumentExtractTool.parseHtmlTables("")).isEmpty();
        assertThat(DocumentExtractTool.parseHtmlTables(null)).isEmpty();
    }
}
