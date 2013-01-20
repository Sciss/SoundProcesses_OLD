/*
 *  ProcFactoryBuilder.scala
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

import impl.FactoryBuilderImpl
import de.sciss.synth.ugen.In
import de.sciss.synth.GraphFunction

/**
 *    @version 0.15, 11-Aug-10
 */
trait ProcFactoryBuilder {
   def name : String
   def pScalar( name: String, spec: ParamSpec, default: Double ) : ProcParamScalar
   def pControl( name: String, spec: ParamSpec, default: Double ) : ProcParamControl
   def pAudio( name: String, spec: ParamSpec, default: Double ) : ProcParamAudio
//   def pString( name: String, default: Option[ String ]) : ProcParamString
   def pAudioIn( name: String, default: Option[ RichAudioBus ]) : ProcParamAudioInput
   def pAudioOut( name: String, default: Option[ RichAudioBus ]) : ProcParamAudioOutput

   def graphOut[ T : GraphFunction.Result ]( thunk: => T ) : ProcGraph
   def graph( thunk: => Any ) : ProcGraph
   def graphIn( fun: In => Any ) : ProcGraph
   def graphInOut[ T : GraphFunction.Result ]( fun: In => T ) : ProcGraph

   def idleOut( fun: () => Int ) : ProcIdle
   def idle : ProcIdle
   def idleIn( fun: Int => Any ) : ProcIdle
   def idleInOut( fun: Int => Any ) : ProcIdle

   def finish : ProcFactory

   def anatomy: ProcAnatomy
}

object ProcFactoryBuilder extends ThreadLocalObject[ ProcFactoryBuilder ] {
   def gen( name: String )( thunk: => Unit ) : ProcFactory =
      apply( FactoryBuilderImpl.gen( name ), thunk )

   def filter( name: String )( thunk: => Unit ) : ProcFactory =
      apply( FactoryBuilderImpl.filter( name ), thunk )

   def diff( name: String )( thunk: => Unit ) : ProcFactory =
      apply( FactoryBuilderImpl.diff( name ), thunk )

   private def apply( b: ProcFactoryBuilder, thunk: => Unit ) : ProcFactory = use( b ) {
      thunk
      b.finish
   }
}