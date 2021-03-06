/* Soot - a J*va Optimization Framework
 * Copyright (C) 1997-1999 Raja Vallee-Rai
 *       updated 2002 Florian Loitsch
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/*
 * Modified by the Sable Research Group and others 1997-1999.  
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */


package soot.toolkits.scalar;

import java.util.*;


/**
 *   Reference implementation for a BoundedFlowSet. Items are stored in an Array.  
 */
public class ArrayPackedSet<T> extends AbstractBoundedFlowSet<T>
{
    ObjectIntMapper<T> map;
    int[] bits;

    public ArrayPackedSet(FlowUniverse<T> universe) {
        this(new ObjectIntMapper<T>(universe));
    }

    ArrayPackedSet(ObjectIntMapper<T> map)
    {
        //int size = universe.getSize();

        //int numWords = size / 32 + (((size % 32) != 0) ? 1 : 0);

        this(map, new int[map.size() / 32 + (((map.size() % 32) != 0) ? 1 : 0)]);
    }
    
    ArrayPackedSet(ObjectIntMapper<T> map, int[] bits)
    {
        this.map = map;
        this.bits = bits.clone();
    }

    /** Returns true if flowSet is the same type of flow set as this. */
    @SuppressWarnings("rawtypes")
	private boolean sameType(Object flowSet)
    {
        return (flowSet instanceof ArrayPackedSet &&
                ((ArrayPackedSet)flowSet).map == map);
    }

    public ArrayPackedSet<T> clone()
    {
        return new ArrayPackedSet<T>(map, bits);
    }

    public FlowSet<T> emptySet()
    {
        return new ArrayPackedSet<T>(map);
    }

    public int size()
    {
        int count = 0;

        for (int word : bits) {
            for(int j = 0; j < 32; j++)
                if((word & (1 << j)) != 0)
                    count++;
        }

        return count;
    }

    public boolean isEmpty()
    {
        for (int element : bits)
			if(element != 0)
                return false;

        return true;
    }


    public void clear()
    {
        for(int i = 0; i < bits.length; i++)
            bits[i] = 0;
    }


    public List<T> toList(int low, int high)
    {
        List<T> elements = new ArrayList<T>();

        int startWord = low / 32,
            startBit = low % 32;

        int endWord = high / 32,
            endBit = high % 32;

        if(low > high)
            return elements;

        // Do the first word
        {
            int word = bits[startWord];

            int offset = startWord * 32;
            int lastBit = (startWord != endWord) ? 32 : (endBit + 1);

            for(int j = startBit; j < lastBit; j++)
            {
                if((word & (1 << j)) != 0)
                    elements.add(map.getObject(offset + j));
            }
        }

        // Do the in between ones
            if(startWord != endWord && startWord + 1 != endWord)
            {
                for(int i = startWord + 1; i < endWord; i++)
                {
                    int word = bits[i];
                    int offset = i * 32;

                    for(int j = 0; j < 32; j++)
                    {
                        if((word & (1 << j)) != 0)
                            elements.add(map.getObject(offset + j));
                    }
                }
            }

        // Do the last one
            if(startWord != endWord)
            {
                int word = bits[endWord];
                int offset = endWord * 32;
                int lastBit = endBit + 1;

                for(int j = 0; j < lastBit; j++)
                {
                    if((word & (1 << j)) != 0)
                        elements.add(map.getObject(offset + j));
                }
            }

        return elements;
    }


    public List<T> toList()
    {
        List<T> elements = new ArrayList<T>();

        for(int i = 0; i < bits.length; i++)
        {
            int word = bits[i];
            int offset = i * 32;

            for(int j = 0; j < 32; j++)
                if((word & (1 << j)) != 0)
                    elements.add(map.getObject(offset + j));
        }

        return elements;
    }

    public void add(T obj)
    {
        int bitNum = map.getInt(obj);

        bits[bitNum / 32] |= 1 << (bitNum % 32);
    }

    public void complement(FlowSet<T> destFlow)
    {
      if (sameType(destFlow)) {
        ArrayPackedSet<T> dest = (ArrayPackedSet<T>) destFlow;

        for(int i = 0; i < bits.length; i++)
            dest.bits[i] = ~(this.bits[i]);
            
        // Clear the bits which are outside of this universe
            if(bits.length >= 1)
            {
                int lastValidBitCount = map.size() % 32;
                
                if(lastValidBitCount != 0)
                    dest.bits[bits.length - 1] &= ~(0xFFFFFFFF << lastValidBitCount);  
            }
      } else
        super.complement(destFlow);
    }

    public void remove(T obj)
    {
        int bitNum = map.getInt(obj);

        bits[bitNum / 32] &= ~(1 << (bitNum % 32));
    }

    public void union(FlowSet<T> otherFlow, FlowSet<T> destFlow)
    {
      if (sameType(otherFlow) &&
          sameType(destFlow)) {
        ArrayPackedSet<T> other = (ArrayPackedSet<T>) otherFlow;
        ArrayPackedSet<T> dest = (ArrayPackedSet<T>) destFlow;

        if(!(other instanceof ArrayPackedSet) || bits.length != other.bits.length)
            throw new RuntimeException("Incompatible other set for union");

        for(int i = 0; i < bits.length; i++)
            dest.bits[i] = this.bits[i] | other.bits[i];
      } else
        super.union(otherFlow, destFlow);
    }

    public void difference(FlowSet<T> otherFlow, FlowSet<T> destFlow)
    {
      if (sameType(otherFlow) &&
          sameType(destFlow)) {

        if(!(otherFlow instanceof ArrayPackedSet))
            throw new RuntimeException("Incompatible other set for union");

        ArrayPackedSet<T> other = (ArrayPackedSet<T>) otherFlow;
        ArrayPackedSet<T> dest = (ArrayPackedSet<T>) destFlow;
        
        if (bits.length != other.bits.length)
            throw new RuntimeException("Incompatible other set for union");

        for(int i = 0; i < bits.length; i++)
            dest.bits[i] = this.bits[i] & ~other.bits[i];
      } else
        super.difference(otherFlow, destFlow);
    }
    
    public void intersection(FlowSet<T> otherFlow, FlowSet<T> destFlow)
    {
      if (sameType(otherFlow) &&
          sameType(destFlow)) {
        if(!(otherFlow instanceof ArrayPackedSet))
            throw new RuntimeException("Incompatible other set for union");

        ArrayPackedSet<T> other = (ArrayPackedSet<T>) otherFlow;
        ArrayPackedSet<T> dest = (ArrayPackedSet<T>) destFlow;
        
        if (bits.length != other.bits.length)
            throw new RuntimeException("Incompatible other set for union");

        for(int i = 0; i < bits.length; i++)
            dest.bits[i] = this.bits[i] & other.bits[i];
      } else
        super.intersection(otherFlow, destFlow);
    }

  /** Returns true, if the object is in the set.
   */
    public boolean contains(T obj)
    {
      /* check if the object is in the map, direct call of map.getInt will
       * add the object into the map.
       */
        if (!map.contains(obj)) return false;

        int bitNum = map.getInt(obj);

        return (bits[bitNum / 32] & (1 << (bitNum % 32))) != 0;
    }

    @SuppressWarnings("unchecked")
	public boolean equals(Object otherFlow)
    {
      if (sameType(otherFlow)) {
        return Arrays.equals(bits, ((ArrayPackedSet<T>)otherFlow).bits);
      } else
        return super.equals(otherFlow);
    }

    public void copy(FlowSet<T> destFlow)
    {
      if (sameType(destFlow)) {
        ArrayPackedSet<T> dest = (ArrayPackedSet<T>) destFlow;

        for(int i = 0; i < bits.length; i++)
            dest.bits[i] = this.bits[i];
      } else
        super.copy(destFlow);
    }

	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			
			int wordIndex = 0;
			int bitIndex = -1;
			
			@Override
			public boolean hasNext() {
		        for (int i = wordIndex; i < bits.length; i++) {
		            int word = bits[i];
		            for (int j = (i == wordIndex ? bitIndex + 1 : 0); j < 32; j++)
		                if((word & (1 << j)) != 0)
		                    return true;
		        }
	            return false;
			}

			@Override
			public T next() {
		        for (int i = wordIndex; i < bits.length; i++) {
		            int word = bits[i];
		            int offset = i * 32;
		            for (int j = (i == wordIndex ? bitIndex + 1 : 0); j < 32; j++)
		                if((word & (1 << j)) != 0) {
		                	wordIndex = i;
		                	bitIndex = j;
		                    return map.getObject(offset + j);
		                }
		        }
				return null;
			}

			@Override
			public void remove() {
		        bits[wordIndex] &= ~(1 << (bitIndex));
			}
			
		};
	}

}

