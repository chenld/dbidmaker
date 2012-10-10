package com.rosolovskiy.database;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * DbIdMaker generates pseudo unique 63 bits ids. It can be
 * used for generating database ids for entities as replace
 * of auto increment keys. It useful in distributed environment,
 * where it is hard to handle numeric sequence.
 *
 * @author Aleksey Rosolovskiy
 */
public class DbIdMaker {

    private final long machineIdSize = 10L; // 10 bits
    private final long maxMachineId = -1L ^ (-1L << machineIdSize);
    private final long sequenceSize = 12L; // 12 bits
    private final long maxSequenceValue = -1L ^ (-1L << sequenceSize);

    private final long machineIdLeftShift = sequenceSize;
    private final long timestampLeftShift = sequenceSize + machineIdSize;

    private final long machineId;

    private long lastTime = -1L;
    private long sequence = 0L;

    /**
     * Initializes DbIdMaker with machine mac address as machineId.
     * @throws UnknownHostException
     * @throws SocketException
     */
    public DbIdMaker() throws UnknownHostException, SocketException {
        final InetAddress ip = InetAddress.getLocalHost();
        final NetworkInterface network = NetworkInterface.getByInetAddress(ip);
        final byte[] mac = network.getHardwareAddress();
        final byte[] macMachineId = new byte[] { mac[3], mac[4], mac[5]};
        machineId = new BigInteger(macMachineId).longValue() % maxMachineId;
    }

    /**
     * Initializes DbIdMaker with given machine id.
     * @param machineId Should be unique in cluster and 10 bits maximum size. (<= 1023)
     */
    public DbIdMaker(final long machineId) {
        if (machineId > maxMachineId || machineId < 0) {
            throw new IllegalArgumentException("Machine argument must be between 0 and " + maxMachineId);
        }
        this.machineId = machineId;
    }

    synchronized public long makeId() {
        long timestamp = time();

        if (timestamp < lastTime) {
            throw new IllegalStateException(String.format("Clock is in past. Please, repeat an attempt after %d milliseconds", lastTime - timestamp));
        }

        if (lastTime == timestamp) {
            sequence = (sequence + 1) & maxSequenceValue;
            if (sequence == 0) {
                // we should wait till next timestamp, if sequence cycle exhausted
                while (timestamp <= lastTime) {
                    timestamp = time();
                }
            }
        } else {
            sequence = 0;
        }

        lastTime = timestamp;
        return (timestamp << timestampLeftShift) | (machineId << machineIdLeftShift) | sequence;
    }

    protected long time() {
        return System.currentTimeMillis();
    }

}