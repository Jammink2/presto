/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator.aggregation.state;

import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.type.Type;

import static com.facebook.presto.spi.type.DoubleType.DOUBLE;

public class NullableDoubleStateSerializer
        implements AccumulatorStateSerializer<NullableDoubleState>
{
    private final Type type;

    public NullableDoubleStateSerializer()
    {
        this(DOUBLE);
    }

    public NullableDoubleStateSerializer(Type type)
    {
        this.type = type;
    }

    @Override
    public Type getSerializedType()
    {
        return type;
    }

    @Override
    public void serialize(NullableDoubleState state, BlockBuilder out)
    {
        if (state.isNull()) {
            out.appendNull();
        }
        else {
            type.writeDouble(out, state.getDouble());
        }
    }

    @Override
    public void deserialize(Block block, int index, NullableDoubleState state)
    {
        state.setNull(block.isNull(index));
        if (!state.isNull()) {
            state.setDouble(type.getDouble(block, index));
        }
    }
}
