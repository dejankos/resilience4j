/*
 * Copyright 2019 Robert Winkler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.circuitbreaker.operator;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.internal.disposables.EmptyDisposable;

import static java.util.Objects.requireNonNull;

class MaybeCircuitBreaker<T> extends Maybe<T> {

    private final Maybe<T> upstream;
    private final CircuitBreaker circuitBreaker;

    MaybeCircuitBreaker(Maybe<T> upstream, CircuitBreaker circuitBreaker) {
        this.upstream = upstream;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    protected void subscribeActual(MaybeObserver<? super T> downstream) {
        if(circuitBreaker.tryAcquirePermission()){
            upstream.subscribe(new CircuitBreakerMaybeObserver(downstream));
        }else{
            downstream.onSubscribe(EmptyDisposable.INSTANCE);
            downstream.onError(new CallNotPermittedException(circuitBreaker));
        }
    }

    class CircuitBreakerMaybeObserver extends BaseCircuitBreakerObserver implements MaybeObserver<T> {

        private final MaybeObserver<? super T> downstreamObserver;

        CircuitBreakerMaybeObserver(MaybeObserver<? super T> childObserver) {
            super(circuitBreaker);
            this.downstreamObserver = requireNonNull(childObserver);
        }

        @Override
        protected void hookOnSubscribe() {
            downstreamObserver.onSubscribe(this);
        }

        @Override
        public void onSuccess(T value) {
            if (!isDisposed()) {
                super.onSuccess();
                downstreamObserver.onSuccess(value);
            }
        }

        @Override
        public void onError(Throwable e) {
            whenNotCompleted(() -> {
                super.onError(e);
                downstreamObserver.onError(e);
            });
        }

        @Override
        public void onComplete() {
            whenNotCompleted(() -> {
                super.onSuccess();
                downstreamObserver.onComplete();
            });
        }
    }
}
