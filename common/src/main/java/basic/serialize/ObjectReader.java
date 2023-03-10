package basic.serialize;

import basic.message.CodedBuffer;
import basic.message.Message;
import basic.protocol.Protocol;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;

import static basic.serialize.ObjectType.*;


/**
 * 用于对象反序列化
 *
 * @author quanchangnai
 */
public class ObjectReader {

    protected CodedBuffer buffer;

    protected TransferableRegistry transferableRegistry;

    protected Function<Integer, Message> messageFactory;

    public ObjectReader(CodedBuffer buffer) {
        this.buffer = Objects.requireNonNull(buffer);
    }

    public void setTransferableRegistry(TransferableRegistry transferableRegistry) {
        this.transferableRegistry = Objects.requireNonNull(transferableRegistry);
    }

    public void setMessageFactory(Function<Integer, Message> messageFactory) {
        this.messageFactory = Objects.requireNonNull(messageFactory);
    }

    public CodedBuffer getBuffer() {
        return buffer;
    }

    @SuppressWarnings("unchecked")
    public <T> T read() {
        return (T) readAny();
    }

    public Object readAny() {
        int type = buffer.readInt();
        switch (type) {
            case NULL:
                return null;
            case BYTE:
                return buffer.readByte();
            case BYTE_ARRAY:
                return buffer.readBytes();
            case BOOLEAN:
                return buffer.readBool();
            case BOOLEAN_ARRAY:
                return readBooleanArray();
            case SHORT:
                return buffer.readShort();
            case SHORT_ARRAY:
                return readShortArray();
            case INTEGER:
                return buffer.readInt();
            case OPTIONAL_INT:
                return readOptionalInt();
            case INT_ARRAY:
                return readIntArray();
            case LONG:
                return buffer.readLong();
            case OPTIONAL_LONG:
                return readOptionalLong();
            case LONG_ARRAY:
                return readLongArray();
            case FLOAT:
                return buffer.readFloat();
            case FLOAT_ARRAY:
                return readFloatArray();
            case DOUBLE:
                return buffer.readDouble();
            case OPTIONAL_DOUBLE:
                return readOptionalDouble();
            case DOUBLE_ARRAY:
                return readDoubleArray();
            case STRING:
                return buffer.readString();
            case STRING_ARRAY:
                return readStringArray();
            case OBJECT:
                return new Object();
            case OBJECT_ARRAY:
                return readObjectArray();
            case ENUM:
                return readEnum();
            case ARRAY_LIST:
                return readCollection(new ArrayList<>());
            case SORTED_SET:
                return readCollection(new TreeSet<>());
            case HASH_SET:
                return readCollection(new HashSet<>());
            case LINKED_LIST:
                return readCollection(new LinkedList<>());
            case ARRAY_DEQUE:
                return readCollection(new ArrayDeque<>());
            case HASH_MAP:
                return readMap(new HashMap<>());
            case SORTED_MAP:
                return readMap(new TreeMap<>());
            case PROTOCOL:
                return readProtocol();
            case TRANSFERABLE:
                return readTransferable();
            case MESSAGE:
                return readMessage();
            case SERIALIZABLE:
                return readSerializable();
            default:
                return readOther(type);
        }
    }


    private boolean[] readBooleanArray() {
        int length = buffer.readInt();
        boolean[] array = new boolean[length];
        for (int i = 0; i < length; i++) {
            array[i] = buffer.readBool();
        }
        return array;
    }

    private short[] readShortArray() {
        int length = buffer.readInt();
        short[] array = new short[length];
        for (int i = 0; i < length; i++) {
            array[i] = buffer.readShort();
        }
        return array;
    }

    private OptionalInt readOptionalInt() {
        if (buffer.readBool()) {
            return OptionalInt.of(buffer.readInt());
        } else {
            return OptionalInt.empty();
        }
    }

    private int[] readIntArray() {
        int length = buffer.readInt();
        int[] array = new int[length];
        for (int i = 0; i < length; i++) {
            array[i] = buffer.readInt();
        }
        return array;
    }

    private OptionalLong readOptionalLong() {
        if (buffer.readBool()) {
            return OptionalLong.of(buffer.readLong());
        } else {
            return OptionalLong.empty();
        }
    }

    private long[] readLongArray() {
        int length = buffer.readInt();
        long[] array = new long[length];
        for (int i = 0; i < length; i++) {
            array[i] = buffer.readLong();
        }
        return array;
    }

    private float[] readFloatArray() {
        int length = buffer.readInt();
        float[] array = new float[length];
        for (int i = 0; i < length; i++) {
            array[i] = buffer.readFloat();
        }
        return array;
    }

    private OptionalDouble readOptionalDouble() {
        if (buffer.readBool()) {
            return OptionalDouble.of(buffer.readDouble());
        } else {
            return OptionalDouble.empty();
        }
    }

    private double[] readDoubleArray() {
        int length = buffer.readInt();
        double[] array = new double[length];
        for (int i = 0; i < length; i++) {
            array[i] = buffer.readDouble();
        }
        return array;
    }

    private String[] readStringArray() {
        int length = buffer.readInt();
        String[] array = new String[length];
        for (int i = 0; i < length; i++) {
            array[i] = buffer.readString();
        }
        return array;
    }

    private Object[] readObjectArray() {
        int length = buffer.readInt();
        Object[] array = new Object[length];
        for (int i = 0; i < length; i++) {
            array[i] = readAny();
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    public <T> T[] readArray(Class<T> componentType) {
        int length = buffer.readInt();
        T[] array = (T[]) Array.newInstance(componentType, length);
        for (int i = 0; i < length; i++) {
            array[i] = (T) readAny();
        }
        return array;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Enum readEnum() {
        Class enumClass;
        try {
            enumClass = Class.forName(buffer.readString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Enum.valueOf(enumClass, buffer.readString());
    }


    protected Collection<Object> readCollection(Collection<Object> collection) {
        int size = buffer.readInt();
        for (int i = 0; i < size; i++) {
            collection.add(readAny());
        }
        return collection;
    }

    protected Map<Object, Object> readMap(Map<Object, Object> map) {
        int size = buffer.readInt();
        for (int i = 0; i < size; i++) {
            map.put(readAny(), readAny());
        }
        return map;
    }

    protected Protocol readProtocol() {
        Protocol protocol = (Protocol) Protocol.getRegistry().create(buffer.readInt());
        protocol.transferFrom(this);
        return protocol;
    }

    protected Transferable readTransferable() {
        int id = buffer.readInt();
        Transferable transferable = transferableRegistry.create(id);
        transferable.transferFrom(this);
        return transferable;
    }

    protected Message readMessage() {
        buffer.mark();
        int id = buffer.readInt();
        buffer.reset();
        Message message = messageFactory.apply(id);
        message.decode(buffer);
        return message;
    }

    protected Object readSerializable() {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer.readBytes());
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            return objectInputStream.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Object readOther(int type) {
        throw new RuntimeException("不支持的数据类型:" + type);
    }

}
