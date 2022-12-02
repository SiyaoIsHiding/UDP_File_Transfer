# Multi-thread Reliable UDP File Transfer
Coursework project following Java Network Programming. 

## Schema
### Request Schema 1024 bytes in total
For index:

Only 1 byte of 1

For get file:

First byte of 2, followed by the 2 byte integer of sequence number, followed by file name

### Response Schema 1024 bytes in total
For index:

immediately list files

For get file:

First two bytes of integer sequence number, one byte of end of file ( 0 for data, 1 for end, 2 for file not found), and then the data in 1021 bytes
