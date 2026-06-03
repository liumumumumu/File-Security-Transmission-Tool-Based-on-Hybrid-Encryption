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
    public static final byte Onlie_User_Search_Request=18;
    public static final byte Onlie_User_Search_Result=19;
    public static final byte Transfer_Cancel=20;
    public static final byte Transfer_Cancel_Ack=21;
    public static final byte Retransmit_Request=22;
    public static final byte Retransmit_Ack=23;

    public static final byte Direct_Session_Hello=30;
    public static final byte Direct_Session_Challenge=31;
    public static final byte Direct_Session_Proof=32;
    public static final byte Direct_Session_Accepted=33;

<<<<<<< HEAD
    public static final byte Text_Message=40;
    public static final byte Text_Message_Ack=41;
    public static final byte Text_Message_Read_Receipt=42;

=======
>>>>>>> origin/main
    public static final byte Ping=80;
    public static final byte Pong=81;
    public static final byte Error=99;
}

/*
消息编号表
 */
