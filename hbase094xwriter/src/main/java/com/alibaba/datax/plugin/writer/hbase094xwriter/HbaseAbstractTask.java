package com.alibaba.datax.plugin.writer.hbase094xwriter;

import com.alibaba.datax.common.element.Column;
import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.util.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

public abstract class HbaseAbstractTask {
    private final static Logger LOG = LoggerFactory.getLogger(HbaseAbstractTask.class);

    public NullModeType nullMode = null;

    public List<Configuration> columns;
    public List<Configuration> rowkeyColumn;
    public Configuration versionColumn;


    public HTable htable;
    public String encoding;
    public Boolean walFlag;


    public HbaseAbstractTask(com.alibaba.datax.common.util.Configuration configuration) {
        this.htable = Hbase094xHelper.getTable(configuration);
        this.columns = configuration.getListConfiguration(Key.COLUMN);
        this.rowkeyColumn = configuration.getListConfiguration(Key.ROWKEY_COLUMN);
        this.versionColumn = configuration.getConfiguration(Key.VERSION_COLUMN);
        this.encoding = configuration.getString(Key.ENCODING,Constant.DEFAULT_ENCODING);
        this.nullMode = NullModeType.getByTypeName(configuration.getString(Key.NULL_MODE,Constant.DEFAULT_NULL_MODE));
        this.walFlag = configuration.getBool(Key.WAL_FLAG, false);
    }

    public void startWriter(RecordReceiver lineReceiver,TaskPluginCollector taskPluginCollector){
        Record record = null;
        try {
            while ((record = lineReceiver.getFromReader()) != null) {
                Put put = null;
                try {
                    put = convertRecordToPut(record);
                } catch (Exception e) {
                    taskPluginCollector.collectDirtyRecord(record, e);
                    continue;
                }
                this.htable.put(put);
            }
        }catch (IOException e){
            throw DataXException.asDataXException(Hbase094xWriterErrorCode.PUT_HBASE_ERROR,e);
        }finally {
            Hbase094xHelper.closeTable(this.htable);
        }
    }


    public abstract  Put convertRecordToPut(Record record);

    public void close()  {
        Hbase094xHelper.closeTable(this.htable);
    }

    public byte[] getColumnByte(ColumnType columnType, Column column){
        byte[] bytes = null;
        if(column.getRawData() != null){
            switch (columnType) {
                case INT:
                    bytes = Bytes.toBytes(column.asLong().intValue());
                    break;
                case LONG:
                    bytes = Bytes.toBytes(column.asLong());
                    break;
                case DOUBLE:
                    bytes = Bytes.toBytes(column.asDouble());
                    break;
                case FLOAT:
                    bytes = Bytes.toBytes(column.asDouble().floatValue());
                    break;
                case SHORT:
                    bytes = Bytes.toBytes(column.asLong().shortValue());
                    break;
                case BOOLEAN:
                    bytes = Bytes.toBytes(column.asBoolean());
                    break;
                case STRING:
                    bytes = this.getValueByte(columnType,column.asString());
                    break;
                default:
                    throw DataXException.asDataXException(Hbase094xWriterErrorCode.ILLEGAL_VALUE, "HbaseWriter列不支持您配置的列类型:" + columnType);
            }
        }else{
            switch (nullMode){
                case Skip:
                    bytes =  null;
                    break;
                case Empty:
                    bytes = HConstants.EMPTY_BYTE_ARRAY;
                    break;
                default:
                    throw DataXException.asDataXException(Hbase094xWriterErrorCode.ILLEGAL_VALUE, "HbaseWriter nullMode不支持您配置的类型,只支持skip或者empty");
            }
        }
        return  bytes;
    }

    public byte[] getValueByte(ColumnType columnType, String value){
        byte[] bytes = null;
        if(value != null){
            switch (columnType) {
                case INT:
                    bytes = Bytes.toBytes(Integer.parseInt(value));
                    break;
                case LONG:
                    bytes = Bytes.toBytes(Long.parseLong(value));
                    break;
                case DOUBLE:
                    bytes = Bytes.toBytes(Double.parseDouble(value));
                    break;
                case FLOAT:
                    bytes = Bytes.toBytes(Float.parseFloat(value));
                    break;
                case SHORT:
                    bytes = Bytes.toBytes(Short.parseShort(value));
                    break;
                case BOOLEAN:
                    bytes = Bytes.toBytes(Boolean.parseBoolean(value));
                    break;
                case STRING:
                    bytes = value.getBytes(Charset.forName(encoding));
                    break;
                default:
                    throw DataXException.asDataXException(Hbase094xWriterErrorCode.ILLEGAL_VALUE, "HbaseWriter列不支持您配置的列类型:" + columnType);
            }
        }else{
            bytes = HConstants.EMPTY_BYTE_ARRAY;
        }
        return  bytes;
    }
}
