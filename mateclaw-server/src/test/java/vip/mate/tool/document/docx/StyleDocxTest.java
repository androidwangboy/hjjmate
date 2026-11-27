package vip.mate.tool.document.docx;

import java.io.File;

/**
 * 业务聚合
 *
 * @version [V2.0]
 * @package vip.mate.tool.document.docx;
 * @description:[用一句话描述该文件做什么]
 * @author: [zhuben]
 * @date: [2026-11-11 11:34]
 * @copyright: 2026 echovin.com
 */

public class StyleDocxTest {
    public static void main(String[] args) {
        String templateJson = "{\n" +
                "    \"ctr\": [\n" +
                "        [\n" +
                "            {\n" +
                "                \"type\": \"p\",\n" +
                "                \"className\": \"\",\n" +
                "                \"data\": {\n" +
                "                    \"style\": \"font-size: 24px; text-align: center;\",\n" +
                "                    \"className\": \"\",\n" +
                "                    \"value\": [\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"20190803161210974f7756c492536154\"\n" +
                "                        }\n" +
                "                    ]\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"p\",\n" +
                "                \"className\": \"\",\n" +
                "                \"data\": {\n" +
                "                    \"style\": \"font-size: 18px; text-align: center;\",\n" +
                "                    \"className\": \"\",\n" +
                "                    \"value\": [\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"20190803161332065d6607a6aeab0c0e\"\n" +
                "                        }\n" +
                "                    ]\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"p\",\n" +
                "                \"className\": \"\",\n" +
                "                \"data\": {\n" +
                "                    \"style\": \"text-align: left; font-size: 14px;\",\n" +
                "                    \"className\": \"\",\n" +
                "                    \"value\": [\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"1747984932433_1\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_0\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"20190803161516097a720b18c33e44c9\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_1\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"201908031614275755d3a8d904d3e533\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_2\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"20190803161841282aaaaa958ebd82fa\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_3\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"201908121148563249b217d93a2b38b0\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_4\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"20190803161610850f2ec73b99d65f0c\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_5\"\n" +
                "                        }\n" +
                "                    ]\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"p\",\n" +
                "                \"className\": \"\",\n" +
                "                \"data\": {\n" +
                "                    \"style\": \"text-align: left; font-size: 14px;\",\n" +
                "                    \"className\": \"\",\n" +
                "                    \"value\": [\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"1747985119739_2\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_6\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"1747985187156_3\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"text\",\n" +
                "                            \"ctlId\": \"temp_text_7\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"c1747985230187\"\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"type\": \"ctl\",\n" +
                "                            \"dropName\": 0,\n" +
                "                            \"ctlId\": \"1747985249135_4\"\n" +
                "                        }\n" +
                "                    ]\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"hr\"\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"tablerow\",\n" +
                "                \"className\": \"\",\n" +
                "                \"style\": \"width: 1436px;\",\n" +
                "                \"flowBox\": null,\n" +
                "                \"tableid\": null,\n" +
                "                \"data\": {\n" +
                "                    \"0\": [\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 0,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"1\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 43px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 1,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"2\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 41px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 2,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"4\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 53px; height: 37px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 3,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"3\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 53px; height: 37px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 4,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"5\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 31px; height: 34px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 5,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"7\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 31px; height: 34px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 6,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"9\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 33px; height: 37px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 7,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"42\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 34px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 8,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"44\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 34px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 9,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"10\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 34px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 10,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"12\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 11,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"13\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 12,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"23\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 13,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"18\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 14,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"17\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 15,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"15\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 16,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"22\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 17,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"1747990212888_5\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 18,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"20\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 40px; height: 37px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 19,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"21\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 40px; height: 37px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 20,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 2,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"32\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 78px; height: 34px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 0,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 21,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 2,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"c1748334217466\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 78px; height: 34px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 0,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 22,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"26\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 39px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 23,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"27\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 24,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"39\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 50px; height: 37px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 0,\n" +
                "                            \"col\": 25,\n" +
                "                            \"rowspan\": 2,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"54\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 34px; height: 37px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        }\n" +
                "                    ],\n" +
                "                    \"1\": [\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 4,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"6\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 31px; height: 3px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 5,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"8\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 31px; height: 3px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 7,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"43\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 3px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 8,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"45\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 3px;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 9,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"11\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 25px; height: 3px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 20,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"31\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 39px; height: 3px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 21,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"41\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 39px; height: 3px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 22,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"c1748334340401\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 39px; height: 3px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 1,\n" +
                "                            \"row\": 1,\n" +
                "                            \"col\": 23,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"value\": [\n" +
                "                                {\n" +
                "                                    \"type\": \"ctl\",\n" +
                "                                    \"opId\": \"c1748334423114\"\n" +
                "                                }\n" +
                "                            ],\n" +
                "                            \"style\": \"width: 39px; height: 3px; text-align: center;\",\n" +
                "                            \"className\": \"\",\n" +
                "                            \"selfMergetd\": null,\n" +
                "                            \"selfExtends\": null,\n" +
                "                            \"borderMergeTd\": null,\n" +
                "                            \"autoGroupid\": null,\n" +
                "                            \"autoGindex\": null\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        },\n" +
                "                        {\n" +
                "                            \"nodeType\": 8,\n" +
                "                            \"row\": 1,\n" +
                "                            \"colspan\": 1,\n" +
                "                            \"rowspan\": 1,\n" +
                "                            \"rowid\": 0\n" +
                "                        }\n" +
                "                    ]\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"p\",\n" +
                "                \"className\": \"\",\n" +
                "                \"data\": {\n" +
                "                    \"style\": \"\",\n" +
                "                    \"className\": \"\",\n" +
                "                    \"value\": []\n" +
                "                }\n" +
                "            }\n" +
                "        ]\n" +
                "    ],\n" +
                "    \"temp\": [\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_0\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp; \"\n" +
                "        },\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_1\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp; \"\n" +
                "        },\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_2\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp; \"\n" +
                "        },\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_3\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp; &nbsp;&nbsp;\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_4\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp; \"\n" +
                "        },\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_5\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp;\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_6\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp;\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"ctl_id\": \"temp_text_7\",\n" +
                "            \"ctl_type\": \"temp_text\",\n" +
                "            \"text\": \"&nbsp;\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"info\": {\n" +
                "        \"file_direction_size\": \"A4\",\n" +
                "        \"file_direction_type\": \"H\",\n" +
                "        \"file_fontname\": \"Microsoft YaHei,微软雅黑,Helvetica Neue,宋体\",\n" +
                "        \"table_width_style\": \"content-table\",\n" +
                "        \"printZoom\": \"0.95\"\n" +
                "    }\n" +
                "}";
    }
}
