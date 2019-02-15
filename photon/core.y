Maybe.include module
  def to_bool(self: Maybe): Bool
    present?
  end

  def unwrap_or(self: Maybe, other: _): _
    if present?
      value
    else
      other
    end
  end

  def unwrap!(self: Maybe): _
    if not present?
      Core.Internal.panic!
    end
  end
end

Struct.include module
  def include(self: Struct, other: Module): Struct
    $module.include other

    self
  end
end
