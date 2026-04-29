package com.common.protocol;

public final class MessageType
{
    public static final byte Auth_Request=1;
    public static final byte Challenge=2;
    public static final byte Auth_Response=3;
    public static final byte Auth_Result=4;

    public static final byte File_Offer=10;
    public static final byte Device_Selection=11;
    public static final byte File_Accept=12;
    public static final byte File_Block=13;
    public static final byte ACk=14;
    public static final byte Transfer_Request=15;
    public static final byte Incoming_Transfer_Request=16;
    public static final byte Receiver_Device_Selection=17;

    public static final byte Ping=20;
    public static final byte Pong=21;
    public static final byte Error=99;
}

//消息编号表
