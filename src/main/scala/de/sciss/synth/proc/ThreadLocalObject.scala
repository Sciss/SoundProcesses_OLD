/*
 *  ThreadLocalObject.scala
 *  (SoundProcesses)
 *
 *  Copyright (c) 2010-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.proc

/**
 *    @version 0.11, 09-Jul-10
 */
//trait ThreadLocalLike[ T <: AnyRef ] {
//   def local : T
//   def use[ U ]( obj: T )( thunk: => U ) : U
//}

trait ThreadLocalObject[ T <: AnyRef ] { // extends ThreadLocalLike[ T ]
   protected val tl = new ThreadLocal[ T ]

   def local : T = {
      val res = tl.get
      require( res != null, "Out of context access" )
      res
   }

   def use[ U ]( obj: T )( thunk: => U ) : U = {
      val old = tl.get()
      tl.set( obj )
      try {
         thunk
      } finally {
         tl.set( old ) // null.asInstanceOf[ T ]
      }
   }
}