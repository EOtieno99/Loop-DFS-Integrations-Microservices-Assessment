package com.example.loop_assessment.soap_resources;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Caps bytes during reception, before allocating an unbounded string or DOM tree. */
final class LimitedBodyHandler implements HttpResponse.BodyHandler<String> {
  private final int maxBytes;

  LimitedBodyHandler(int maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo info) {
    return new HttpResponse.BodySubscriber<>() {
      private final HttpResponse.BodySubscriber<String> delegate =
          HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
      private Flow.Subscription subscription;
      private long received;
      private boolean failed;

      @Override
      public CompletionStage<String> getBody() {
        return delegate.getBody();
      }

      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        delegate.onSubscribe(subscription);
      }

      @Override
      public void onNext(List<ByteBuffer> buffers) {
        if (failed) return;
        for (var buffer : buffers) received += buffer.remaining();
        if (received > maxBytes) {
          failed = true;
          subscription.cancel();
          delegate.onError(new UpstreamException("Country provider response is too large", false));
        } else delegate.onNext(buffers);
      }

      @Override
      public void onError(Throwable failure) {
        if (!failed) delegate.onError(failure);
      }

      @Override
      public void onComplete() {
        if (!failed) delegate.onComplete();
      }
    };
  }
}
