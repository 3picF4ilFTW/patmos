//
//  This file is part of the Patmos Simulator.
//  The Patmos Simulator is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  The Patmos Simulator is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with the Patmos Simulator. If not, see <http://www.gnu.org/licenses/>.
//
//
//  This represents the GDB RSP message layout, i.e. the high level
//  abstraction of rsp packets.
//  Each message handles its effect on its own.
//

#ifndef PATMOS_GDB_MESSAGE_H
#define PATMOS_GDB_MESSAGE_H

#include <string>
#include <boost/shared_ptr.hpp>

namespace patmos
{
  class GdbPacketHandler;

  class GdbUnsupportedMessageException : public std::exception
  {
  public:
    GdbUnsupportedMessageException(std::string packetContent);
    ~GdbUnsupportedMessageException() throw();
    virtual const char* what() const throw();
  
  private:
    std::string m_whatMessage;
  };

  class GdbMessage
  {
  public:
    virtual std::string GetMessageString() const = 0;
    virtual void Handle(const GdbPacketHandler &packethandler) const = 0;
  };

  typedef boost::shared_ptr<GdbMessage> GdbMessagePtr;

  GdbMessagePtr GetGdbMessage(std::string packetContent);

}
#endif // PATMOS_GDB_MESSAGE_H
