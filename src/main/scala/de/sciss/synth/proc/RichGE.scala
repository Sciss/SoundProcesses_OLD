/*
 *  RichGE.scala
 *  (SoundProcesses)
 *
 *  Copyright (c) 2010 Hanns Holger Rutz. All rights reserved.
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
 *
 *
 *  Changelog:
 */

package de.sciss.synth.proc

import de.sciss.synth
import de.sciss.synth.{Constant, GE, ugen}
import impl.SynthReactionImpl
import ugen.{Mix, Impulse}

/**
 * Enrichment for graph elements.
 *
 * @version 0.10, 29-Aug-10
 */
class RichGE( ge: GE ) {
   /**
    * Uses this graph element as a trigger to which a client code
    * fragment reacts. A trigger occurs when the signal passes
    * from non-positive to positive.
    *
    * @param   thunk    the code to execute upon receiving the trigger
    */
   def react( thunk: => Unit ) {
      // XXX eventually we could optimize this by
      // using SendTrig instead of SendReply in this case
      val r    = new SynthReactionImpl( ge, Constant(0), x => thunk )
      val pb   = ProcGraphBuilder.local
      pb.includeReaction( r )
   }

   /**
    * Uses this graph element as a trigger to poll values from
    * a signal and invoke a client function with these sampled values.
    * A trigger occurs when the signal passes
    * from non-positive to positive.
    *
    * For a monophonic values input, you can unpack the sample with something
    * like
    * {{{
    * trigSig.react( valueSig ) { res => val Seq( value ) = res; ... } 
    * }}}
    *
    * @param   values      the signal (potentially multi-channel) to poll
    * @param   fun         the function which receives the values sampled
    *    upon receiving the trigger.
    */
   def react( values: GE )( fun: Seq[ Double ] => Unit ) {
      val r    = new SynthReactionImpl( ge, values, fun )
      val pb   = ProcGraphBuilder.local
      pb.includeReaction( r )
   }
}