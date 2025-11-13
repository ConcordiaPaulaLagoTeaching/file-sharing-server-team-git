package ca.concordia.filesystem;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

import ca.concordia.filesystem.datastructures.FEntry;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    // constructor
    public FileSystemManager(String filename, int totalSize) {
        //  Initialize the file system manager with a file
        if (instance == null) {
            instance = this;
            inodeTable = new FEntry[MAXFILES];
            for (int i = 0; i < MAXFILES; i++) {
                inodeTable[i] = new FEntry("", (short) 0, (short) -1);
            }
            freeBlockList = new boolean[MAXBLOCKS];
            for (int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = true;
            }
            try {
                disk = new RandomAccessFile(filename, "rw");
            } catch (Exception e) {
                throw new RuntimeException("Unable to create virtual disk file", e);
            }
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    // create file
    public void createFile(String fileName) throws Exception {
        // TODO
        globalLock.lock();
        try {
            if (fileName.length() > 11) {
                throw new Exception("ERROR: Filename cannot be longer than 11 characters.");
            }
            // check if file already exists
            for (FEntry e : inodeTable) {
                if (e.getFilename().equals(fileName)) {
                    throw new Exception("ERROR: File already exists.");
                }
            }
            // find a free slot
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i].getFilename().isEmpty()) {
                    inodeTable[i].setFilename(fileName);
                    inodeTable[i].setFilesize((short) 0);
                    inodeTable[i].setFirstBlock((short) -1);
                    return;
                }
            }
            throw new Exception("ERROR: no space for new file");
        } finally {
            globalLock.unlock();
        }
    }

// TODO: Add readFile, writeFile and other required methods
// Helper method
    private int findFileIndex(String fileName) throws Exception {
        for (int i = 0; i < MAXFILES; i++) {
            if (inodeTable[i].getFilename().equals(fileName)) {
                return i;
            }
        }
        throw new Exception("ERROR: file " + fileName + " does not exist");
    }

    private int findFreeBlock() throws Exception {
        for (int i = 0; i < MAXBLOCKS; i++) {
            if (freeBlockList[i]) {
                return i;
            }
        }
        throw new Exception("ERROR: File too large");
    }

    private int countFreeBlocks() {
        int count = 0;
        for (boolean b : freeBlockList) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    private void clearFileEntry(FEntry entry) {
        entry.setFilename("");
        entry.setFilesize((short) 0);
        entry.setFirstBlock((short) -1);
    }

    // Write file
    public void writeFile(String fileName, byte[] content) throws Exception {
        globalLock.lock();
        try {
            int fileIndex = findFileIndex(fileName);
            FEntry entry = inodeTable[fileIndex];

            int blocksNeeded = (int) Math.ceil((double) content.length / BLOCK_SIZE);
            if (blocksNeeded > countFreeBlocks()) {
                throw new Exception("ERROR: file too large");
            }
            short firstBlock = entry.getFirstBlock();
            if (firstBlock >= 0) {
                for (int i = 0; i < MAXBLOCKS; i++) {
                    freeBlockList[i] = true;
                }
            }
            int offset = 0;
            int firstAllocatedBlock = -1;

            for (int b = 0; b < blocksNeeded; b++) {
                int blockIndex = findFreeBlock();
                freeBlockList[blockIndex] = false;

                disk.seek((long) blockIndex * BLOCK_SIZE);
                int len = Math.min(BLOCK_SIZE, content.length - offset);
                disk.write(content, offset, len);
                offset += len;
                if (firstAllocatedBlock == -1) {
                    firstAllocatedBlock = blockIndex;
                }
            }
            entry.setFilesize((short) content.length);
            entry.setFirstBlock((short) firstAllocatedBlock);
        } finally {
            globalLock.unlock();
        }
    }

    // read file
    public byte[] readFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            int fileIndex = findFileIndex(fileName);
            FEntry entry = inodeTable[fileIndex];
            if (entry.getFilesize() == 0) {
                return new byte[0];
            }

            byte[] data = new byte[entry.getFilesize()];
            short firstBlock = entry.getFirstBlock();

            if (firstBlock < 0) {
                throw new Exception("ERROR: file " + fileName + " has no data blocks");
            }

            disk.seek((long) firstBlock * BLOCK_SIZE);
            disk.read(data);

            return data;
        } finally {
            globalLock.unlock();
        }
    }

    // Delete file
    public void deleteFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            int fileIndex = findFileIndex(fileName);
            FEntry entry = inodeTable[fileIndex];

            short firstBlock = entry.getFirstBlock();
            if (firstBlock >= 0 && firstBlock < MAXBLOCKS) {
                freeBlockList[firstBlock] = true;
            }

            clearFileEntry(entry);
        } finally {
            globalLock.unlock();
        }
    }

    // List file
    public String[] listFiles() {
        globalLock.lock();
        try {
            java.util.List<String> list = new java.util.ArrayList<>();
            for (FEntry e : inodeTable) {
                if (!e.getFilename().isEmpty()) {
                    list.add(e.getFilename());
                }
            }
            return list.toArray(new String[0]);
        } finally {
            globalLock.unlock();
        }
    }
}
