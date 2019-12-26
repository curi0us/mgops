#!/usr/bin/python
import socket,time,sys

import os, pwd, grp
def drop_privileges(uid_name='nobody', gid_name='nogroup'):
        if os.getuid() != 0:
                return

        running_uid = pwd.getpwnam(uid_name).pw_uid
        running_gid = grp.getgrnam(gid_name).gr_gid
        os.setgroups([])
        os.setgid(running_gid)
        os.setuid(running_uid)
        old_umask = os.umask(077)


class DNSQuery:
  def __init__(self, data):
    self.data=data
    self.domain=''

    tipo = (ord(data[2]) >> 3) & 15   # Opcode bits
    if tipo == 0:                     # Standard query
      ini=12
      lon=ord(data[ini])
      while lon != 0:
        self.domain+=data[ini+1:ini+lon+1]+'.'
        ini+=lon+1
        lon=ord(data[ini])

  def getip(self,domain):
        ips = {
            "stun":"192.3.217.162",
            "savemgo":"64.111.125.108",
            "mgo2":"192.3.217.61",
            "mgo1":"192.3.217.162",
            "mgo2web":"64.111.125.210",
            "mgo2auth":"64.111.124.163",
            "mgs":"173.236.164.40",
        }
        if 'stun' in domain:
                return ips['stun']
        elif 'tx11.savemgo' in domain:
                return ips['mgo2']
        elif 'savemgo' in domain or 'mgs3sweb' in domain:
                return ips['savemgo']
        elif 'mgs.konamionline' in domain:
                return ips['mgs']
        elif 'mgs3' in domain:
                return ips['mgo1']
        elif 'mgo2gate' in domain:
                return ips['mgo2']
        elif 'mgo2web' in domain or 'mgstpp-game' in domain:
                return ips['mgo2web']
        elif 'mgo2auth' in domain:
                return ips['mgo2auth']
        elif 'gate' in domain:
                return ips['mgo1']
        elif ( 'mgo' in domain or 'mgs' in domain or 'konami' in domain
             or 'sony' in domain or 'playstation' in domain
             or 'mgo2web' in domain or 'mgo2auth' in domain or 'info.service' in domain):
             return ips['mgo2']
        return None

  def response(self):
        packet=''
        ip = self.getip(self.domain)
        if ip is None:
                return None

        packet+=self.data[:2] + "\x81\x80"
        packet+=self.data[4:6] + self.data[4:6] + '\x00\x00\x00\x00'   # Questions and Answers Counts
        packet+=self.data[12:]                                         # Original Domain Name Question
        packet+='\xc0\x0c'                                             # Pointer to domain name
        packet+='\x00\x01\x00\x01\x00\x00\x00\x3c\x00\x04'             # Response type, ttl and resource data length -> 4 bytes
        packet+=str.join('',map(lambda x: chr(int(x)), ip.split('.'))) # 4bytes of IP
        return packet

if __name__ == '__main__':
  udps = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  if len(sys.argv) <= 1:
        sys.argv[1] = ''
  udps.bind((sys.argv[1],53))
  drop_privileges()
  print "[%s] DNS Started." % (time.strftime("%H:%M:%S"))
  while 1:
    try:
        data, addr = udps.recvfrom(1024)
        p=DNSQuery(data)
        packet = p.response()
        print '[%s] %s Requested %s' %  (time.strftime("%H:%M:%S"),addr[0],p.domain)
        if not packet is None:
          udps.sendto(p.response(), addr)
        else:
          print "\tNot Found."
    except KeyboardInterrupt:
        sys.exit(0)
    except:
        pass
